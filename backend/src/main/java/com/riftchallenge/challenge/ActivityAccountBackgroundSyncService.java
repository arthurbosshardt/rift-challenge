package com.riftchallenge.challenge;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.leaderboard.AccountMatchRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountSyncService;
import com.riftchallenge.leaderboard.LeaderboardAccountSyncService.ActivitySyncBatchResult;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import com.riftchallenge.riot.RiotMatchLookupService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Imports ranked solo/duo season matches off the request thread so Mes statistiques
 * can respond immediately from DB while catch-up continues in the background.
 */
@Service
public class ActivityAccountBackgroundSyncService {

    private static final Logger log = LoggerFactory.getLogger(ActivityAccountBackgroundSyncService.class);
    /** Delay between chained catch-up batches — keeps us under the shared dev Riot quota. */
    static final Duration CATCH_UP_RETRY_DELAY = Duration.ofSeconds(45);
    /** Minimum gap before a fresh catch-up chain starts from another page load. */
    static final Duration NEW_CHAIN_COOLDOWN = Duration.ofMinutes(2);
    /** Safety cap: at most this many back-to-back batches per chain (15 matches each). */
    static final int MAX_CATCH_UP_CHAIN = 12;
    /** Rows imported before combat-stat columns existed get patched in batches of this size. */
    static final int COMBAT_STATS_BACKFILL_BATCH = 15;

    private final LeaderboardAccountSyncService accountSyncService;
    private final AccountMatchRepository accountMatchRepository;
    private final RiotAccountRepository riotAccountRepository;
    private final RiotMatchLookupService riotMatchLookupService;
    private final LeaderboardProperties leaderboardProperties;
    private final ExecutorService riotSyncExecutor;
    private final Clock clock;
    private final ConcurrentMap<UUID, Boolean> syncInProgress = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> catchUpChainActive = new ConcurrentHashMap<>();
    /** Season match total recorded at the moment the season history was found exhausted, keyed by
     *  riot account id. A cached exhaustion only still blocks catch-up while the live season total
     *  hasn't grown past this value — otherwise the player has played new games since and a fresh
     *  catch-up chain must run. */
    private final ConcurrentMap<UUID, Integer> seasonHistoryExhaustedAtTotal = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Instant> lastChainStartedAt = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> combatStatsBackfillExhausted = new ConcurrentHashMap<>();

    public ActivityAccountBackgroundSyncService(
            LeaderboardAccountSyncService accountSyncService,
            AccountMatchRepository accountMatchRepository,
            RiotAccountRepository riotAccountRepository,
            RiotMatchLookupService riotMatchLookupService,
            LeaderboardProperties leaderboardProperties,
            ExecutorService riotSyncExecutor,
            Clock clock
    ) {
        this.accountSyncService = accountSyncService;
        this.accountMatchRepository = accountMatchRepository;
        this.riotAccountRepository = riotAccountRepository;
        this.riotMatchLookupService = riotMatchLookupService;
        this.leaderboardProperties = leaderboardProperties;
        this.riotSyncExecutor = riotSyncExecutor;
        this.clock = clock;
    }

    public boolean isCatchUpActive(UUID riotAccountId) {
        return Boolean.TRUE.equals(catchUpChainActive.get(riotAccountId))
                || syncInProgress.containsKey(riotAccountId);
    }

    /** Legacy, total-agnostic check: true if the flag was ever set, regardless of whether it's
     *  since gone stale. Prefer {@link #isSeasonHistoryExhausted(UUID, int)} for anything that
     *  gates a sync decision. */
    public boolean isSeasonHistoryExhausted(UUID riotAccountId) {
        if (seasonHistoryExhaustedAtTotal.containsKey(riotAccountId)) {
            return true;
        }
        return riotAccountRepository.findById(riotAccountId)
                .map(RiotAccount::isActivitySeasonHistoryExhausted)
                .orElse(false);
    }

    /** True only if the account was marked exhausted at a season total that is still >= the
     *  current live season total — i.e. no new games have been played since exhaustion was
     *  recorded. A recorded total of {@code null} (unknown, e.g. rows predating this tracking)
     *  is treated as stale so a fresh catch-up can run instead of staying stuck forever. */
    public boolean isSeasonHistoryExhausted(UUID riotAccountId, int seasonMatchTotal) {
        Integer cachedTotal = seasonHistoryExhaustedAtTotal.get(riotAccountId);
        if (cachedTotal != null) {
            return cachedTotal >= seasonMatchTotal;
        }
        return riotAccountRepository.findById(riotAccountId)
                .filter(RiotAccount::isActivitySeasonHistoryExhausted)
                .map(account -> {
                    Integer persistedTotal = account.getActivitySeasonHistoryExhaustedTotal();
                    return persistedTotal != null && persistedTotal >= seasonMatchTotal;
                })
                .orElse(false);
    }

    public void scheduleSyncIfIdle(RiotAccount account, int seasonMatchTotal, int storedMatchCount) {
        UUID riotAccountId = account.getId();
        if (storedMatchCount >= seasonMatchTotal) {
            catchUpChainActive.remove(riotAccountId);
            seasonHistoryExhaustedAtTotal.remove(riotAccountId);
            clearPersistedSeasonHistoryExhausted(account);
            scheduleCombatStatsBackfillIfIdle(account);
            return;
        }

        if (isSeasonHistoryExhausted(riotAccountId, seasonMatchTotal)) {
            return;
        }

        if (isCatchUpActive(riotAccountId)) {
            return;
        }

        Instant now = clock.instant();
        Instant lastChain = lastChainStartedAt.get(riotAccountId);
        if (lastChain != null && lastChain.plus(NEW_CHAIN_COOLDOWN).isAfter(now) && storedMatchCount > 0) {
            return;
        }

        startCatchUpChain(account, seasonMatchTotal, 0);
    }

    /**
     * Synchronous counterpart to {@link #scheduleSyncIfIdle} for the player-profile "Actualiser"
     * button: runs a single catch-up batch on the calling thread so the click's own response
     * reflects newly-imported matches immediately, instead of waiting on the async chain's 45s
     * cadence. If more than one batch's worth is missing, the remainder continues via the normal
     * background chain exactly as {@link #startCatchUpChain} already does.
     */
    public void syncNowIfIdle(RiotAccount account, int seasonMatchTotal, int storedMatchCount) {
        UUID riotAccountId = account.getId();
        if (storedMatchCount >= seasonMatchTotal) {
            catchUpChainActive.remove(riotAccountId);
            seasonHistoryExhaustedAtTotal.remove(riotAccountId);
            clearPersistedSeasonHistoryExhausted(account);
            return;
        }

        if (isSeasonHistoryExhausted(riotAccountId, seasonMatchTotal)) {
            return;
        }

        if (isCatchUpActive(riotAccountId)) {
            return;
        }

        if (syncInProgress.putIfAbsent(riotAccountId, Boolean.TRUE) != null) {
            return;
        }

        lastChainStartedAt.put(riotAccountId, clock.instant());
        catchUpChainActive.put(riotAccountId, Boolean.TRUE);

        ActivitySyncBatchResult batchResult;
        try {
            riotMatchLookupService.beginRefreshScope();
            try {
                batchResult = accountSyncService.syncAccountForActivity(account, seasonMatchTotal);
            } finally {
                riotMatchLookupService.endRefreshScope();
            }
        } catch (ResponseStatusException exception) {
            catchUpChainActive.remove(riotAccountId);
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Synchronous activity refresh hit Riot rate limit for riot account {}", riotAccountId);
            } else {
                log.warn(
                        "Synchronous activity refresh failed for riot account {}: {}",
                        riotAccountId,
                        exception.getReason()
                );
            }
            return;
        } catch (RuntimeException exception) {
            catchUpChainActive.remove(riotAccountId);
            log.warn("Synchronous activity refresh failed for riot account {}: {}", riotAccountId, exception.getMessage());
            return;
        } finally {
            syncInProgress.remove(riotAccountId);
        }

        scheduleContinuationOrFinish(account, seasonMatchTotal, batchResult, 0);
    }

    void startCatchUpChain(RiotAccount account, int seasonMatchTotal, int chainDepth) {
        UUID riotAccountId = account.getId();
        if (chainDepth >= MAX_CATCH_UP_CHAIN) {
            catchUpChainActive.remove(riotAccountId);
            return;
        }

        if (isSeasonHistoryExhausted(riotAccountId, seasonMatchTotal)) {
            return;
        }

        long storedMatchCount = accountMatchRepository.countSeasonMatchesSince(
                account.getRiotPuuid(),
                leaderboardProperties.seasonStartAt()
        );
        if (storedMatchCount >= seasonMatchTotal) {
            catchUpChainActive.remove(riotAccountId);
            return;
        }

        if (syncInProgress.putIfAbsent(riotAccountId, Boolean.TRUE) != null) {
            return;
        }

        if (chainDepth == 0) {
            lastChainStartedAt.put(riotAccountId, clock.instant());
            catchUpChainActive.put(riotAccountId, Boolean.TRUE);
        }

        riotSyncExecutor.submit(() -> runCatchUpBatch(account, seasonMatchTotal, chainDepth));
    }

    private void runCatchUpBatch(RiotAccount account, int seasonMatchTotal, int chainDepth) {
        UUID riotAccountId = account.getId();
        ActivitySyncBatchResult batchResult = null;
        try {
            riotMatchLookupService.beginRefreshScope();
            try {
                batchResult = accountSyncService.syncAccountForActivity(account, seasonMatchTotal);
            } finally {
                riotMatchLookupService.endRefreshScope();
            }
        } catch (ResponseStatusException exception) {
            catchUpChainActive.remove(riotAccountId);
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Background activity sync hit Riot rate limit for riot account {}", riotAccountId);
            } else {
                log.warn(
                        "Background activity sync failed for riot account {}: {}",
                        riotAccountId,
                        exception.getReason()
                );
            }
            return;
        } catch (RuntimeException exception) {
            catchUpChainActive.remove(riotAccountId);
            log.warn("Background activity sync failed for riot account {}: {}", riotAccountId, exception.getMessage());
            return;
        } finally {
            syncInProgress.remove(riotAccountId);
        }

        scheduleContinuationOrFinish(account, seasonMatchTotal, batchResult, chainDepth);
    }

    /** Shared post-batch bookkeeping for both the async chain and the synchronous refresh path:
     *  records exhaustion if this batch reached the end, and either stops or queues the next
     *  batch after the usual retry delay. */
    private void scheduleContinuationOrFinish(
            RiotAccount account,
            int seasonMatchTotal,
            ActivitySyncBatchResult batchResult,
            int chainDepth
    ) {
        UUID riotAccountId = account.getId();
        long storedMatchCount = accountMatchRepository.countSeasonMatchesSince(
                account.getRiotPuuid(),
                leaderboardProperties.seasonStartAt()
        );
        if (batchResult != null && batchResult.allMatchIdsImported()) {
            // Record exhaustion against what Riot's match-list API actually returned for this
            // window, not the caller-supplied seasonMatchTotal — that total falls back to a large
            // placeholder (see ActivitySeasonMatchTotals.FALLBACK_LIMIT) whenever the rank lookup
            // fails, and persisting that placeholder would wedge catch-up forever since a real
            // season total practically never reaches it.
            int exhaustedAtTotal = batchResult.availableMatches();
            seasonHistoryExhaustedAtTotal.put(riotAccountId, exhaustedAtTotal);
            markPersistedSeasonHistoryExhausted(account, exhaustedAtTotal);
        }
        if (storedMatchCount >= seasonMatchTotal
                || (batchResult != null && batchResult.allMatchIdsImported())
                || chainDepth + 1 >= MAX_CATCH_UP_CHAIN) {
            catchUpChainActive.remove(riotAccountId);
            return;
        }

        riotSyncExecutor.submit(() -> {
            try {
                Thread.sleep(CATCH_UP_RETRY_DELAY.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                catchUpChainActive.remove(riotAccountId);
                return;
            }
            startCatchUpChain(account, seasonMatchTotal, chainDepth + 1);
        });
    }

    /**
     * Season import is caught up, but rows persisted before combat-stat columns existed (see
     * V27__leaderboard_account_match_stats.sql) still have null KDA/CS. Those are only patched by
     * {@link LeaderboardAccountSyncService#backfillMissingCombatStats}, which the catch-up chain
     * above never reaches once storedMatchCount >= seasonMatchTotal — so trigger it separately.
     */
    private void scheduleCombatStatsBackfillIfIdle(RiotAccount account) {
        UUID riotAccountId = account.getId();
        if (Boolean.TRUE.equals(combatStatsBackfillExhausted.get(riotAccountId)) || isCatchUpActive(riotAccountId)) {
            return;
        }

        List<String> missing = accountMatchRepository.findMatchIdsMissingCombatStatsSince(
                account.getRiotPuuid(),
                leaderboardProperties.seasonStartAt(),
                PageRequest.of(0, 1)
        );
        if (missing.isEmpty()) {
            combatStatsBackfillExhausted.put(riotAccountId, Boolean.TRUE);
            return;
        }

        if (syncInProgress.putIfAbsent(riotAccountId, Boolean.TRUE) != null) {
            return;
        }

        riotSyncExecutor.submit(() -> runCombatStatsBackfill(account));
    }

    private void runCombatStatsBackfill(RiotAccount account) {
        UUID riotAccountId = account.getId();
        try {
            riotMatchLookupService.beginRefreshScope();
            try {
                accountSyncService.backfillMissingCombatStats(account.getRiotPuuid(), COMBAT_STATS_BACKFILL_BATCH);
            } finally {
                riotMatchLookupService.endRefreshScope();
            }
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                log.warn("Combat stats backfill hit Riot rate limit for riot account {}", riotAccountId);
            } else {
                log.warn("Combat stats backfill failed for riot account {}: {}", riotAccountId, exception.getReason());
            }
        } catch (RuntimeException exception) {
            log.warn("Combat stats backfill failed for riot account {}: {}", riotAccountId, exception.getMessage());
        } finally {
            syncInProgress.remove(riotAccountId);
        }
    }

    private void markPersistedSeasonHistoryExhausted(RiotAccount account, int seasonMatchTotal) {
        Integer persistedTotal = account.getActivitySeasonHistoryExhaustedTotal();
        if (account.isActivitySeasonHistoryExhausted() && persistedTotal != null && persistedTotal >= seasonMatchTotal) {
            return;
        }
        account.markActivitySeasonHistoryExhausted(seasonMatchTotal);
        riotAccountRepository.save(account);
    }

    private void clearPersistedSeasonHistoryExhausted(RiotAccount account) {
        if (!account.isActivitySeasonHistoryExhausted()) {
            return;
        }
        account.clearActivitySeasonHistoryExhausted();
        riotAccountRepository.save(account);
    }
}
