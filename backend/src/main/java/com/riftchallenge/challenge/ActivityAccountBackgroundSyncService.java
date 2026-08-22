package com.riftchallenge.challenge;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountMatchRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountSyncService;
import com.riftchallenge.leaderboard.LeaderboardAccountSyncService.ActivitySyncBatchResult;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import com.riftchallenge.riot.RiotMatchLookupService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final LeaderboardAccountSyncService accountSyncService;
    private final LeaderboardAccountMatchRepository accountMatchRepository;
    private final RiotAccountRepository riotAccountRepository;
    private final RiotMatchLookupService riotMatchLookupService;
    private final LeaderboardProperties leaderboardProperties;
    private final ExecutorService riotSyncExecutor;
    private final Clock clock;
    private final ConcurrentMap<UUID, Boolean> syncInProgress = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> catchUpChainActive = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> seasonHistoryExhausted = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Instant> lastChainStartedAt = new ConcurrentHashMap<>();

    public ActivityAccountBackgroundSyncService(
            LeaderboardAccountSyncService accountSyncService,
            LeaderboardAccountMatchRepository accountMatchRepository,
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

    public boolean isSeasonHistoryExhausted(UUID riotAccountId) {
        if (Boolean.TRUE.equals(seasonHistoryExhausted.get(riotAccountId))) {
            return true;
        }
        return riotAccountRepository.findById(riotAccountId)
                .map(RiotAccount::isActivitySeasonHistoryExhausted)
                .orElse(false);
    }

    public void scheduleSyncIfIdle(RiotAccount account, int seasonMatchTotal, int storedMatchCount) {
        UUID riotAccountId = account.getId();
        if (storedMatchCount >= seasonMatchTotal) {
            catchUpChainActive.remove(riotAccountId);
            seasonHistoryExhausted.remove(riotAccountId);
            clearPersistedSeasonHistoryExhausted(account);
            return;
        }

        if (isSeasonHistoryExhausted(riotAccountId)) {
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

    void startCatchUpChain(RiotAccount account, int seasonMatchTotal, int chainDepth) {
        UUID riotAccountId = account.getId();
        if (chainDepth >= MAX_CATCH_UP_CHAIN) {
            catchUpChainActive.remove(riotAccountId);
            return;
        }

        if (isSeasonHistoryExhausted(riotAccountId)) {
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

        long storedMatchCount = accountMatchRepository.countSeasonMatchesSince(
                account.getRiotPuuid(),
                leaderboardProperties.seasonStartAt()
        );
        if (batchResult != null && batchResult.allMatchIdsImported()) {
            seasonHistoryExhausted.put(riotAccountId, Boolean.TRUE);
            markPersistedSeasonHistoryExhausted(account);
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

    private void markPersistedSeasonHistoryExhausted(RiotAccount account) {
        if (account.isActivitySeasonHistoryExhausted()) {
            return;
        }
        account.markActivitySeasonHistoryExhausted();
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
