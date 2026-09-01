package com.riftchallenge.leaderboard;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotMatchDurations;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import com.riftchallenge.synchronization.RiotMatch;
import com.riftchallenge.synchronization.RiotMatchRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Syncs every linked Riot account's ranked solo/duo rank and match history for the global
 * leaderboard — deliberately independent of challenges: a player's leaderboard numbers must not
 * depend on which challenge(s) they've joined or how often those get refreshed. Runs only from
 * {@link LeaderboardCacheService#refresh}, same cadence as the leaderboard recompute itself.
 *
 * <p>Tracked accounts aren't region-tagged at creation: {@link #resolveRegion} lazily detects and
 * caches each account's server on its first sync by probing every distinct continental routing.
 */
@Service
public class LeaderboardAccountSyncService {

    /** Representative probe for each distinct continental routing (EUNE shares EUW's "europe"). */
    private static final ChallengeRegion[] PROBE_REGIONS = {ChallengeRegion.EUW, ChallengeRegion.NA, ChallengeRegion.KR};
    /** Unbounded lower bound for {@link #backfillFullHistory}, which fetches full history, not just the current season. */
    private static final long FULL_HISTORY_START_EPOCH_SECONDS = 0L;

    static final int MAX_NEW_MATCHES_PER_SYNC = 10;
    static final int MAX_CATCHUP_MATCHES = 10;
    /**
     * Hard ceiling on match-detail fetches for one whole {@link #syncAllAccounts} pass, regardless
     * of how many accounts are linked. Each fetch can block for a while under sustained Riot rate
     * limiting (the client retries with the server's own Retry-After, observed well past a minute
     * under load) — without this, a large batch of newly linked accounts turns one pass into an
     * effectively unbounded, single-threaded run. Bounding it here means a pass always finishes in
     * bounded time; a large backlog just spreads across more scheduled passes instead of one giant
     * one, the same tradeoff {@link #MAX_NEW_MATCHES_PER_SYNC} already makes per account.
     */
    static final int MAX_MATCH_IMPORTS_PER_PASS = 20;
    /** Per activity refresh: import missing season matches before serving champion stats from DB. */
    public static final int ACTIVITY_MAX_IMPORTS_PER_REFRESH = 10;
    static final int ACTIVITY_MAX_CATCHUP_MATCHES = 10;
    /** Hard ceiling on match-detail fetches per background activity sync (imports + backfills). */
    public static final int ACTIVITY_SYNC_BUDGET = 15;

    private static final Logger log = LoggerFactory.getLogger(LeaderboardAccountSyncService.class);

    private final RiotAccountRepository riotAccountRepository;
    private final AccountMatchRepository accountMatchRepository;
    private final LeaderboardAccountRankRepository accountRankRepository;
    private final LeaderboardAccountRankHistoryRepository accountRankHistoryRepository;
    private final RiotMatchRepository riotMatchRepository;
    private final RiotLeagueClient riotLeagueClient;
    private final RiotMatchClient riotMatchClient;
    private final RiotMatchLookupService riotMatchLookupService;
    private final LeaderboardProperties properties;

    public LeaderboardAccountSyncService(
            RiotAccountRepository riotAccountRepository,
            AccountMatchRepository accountMatchRepository,
            LeaderboardAccountRankRepository accountRankRepository,
            LeaderboardAccountRankHistoryRepository accountRankHistoryRepository,
            RiotMatchRepository riotMatchRepository,
            RiotLeagueClient riotLeagueClient,
            RiotMatchClient riotMatchClient,
            RiotMatchLookupService riotMatchLookupService,
            LeaderboardProperties properties
    ) {
        this.riotAccountRepository = riotAccountRepository;
        this.accountMatchRepository = accountMatchRepository;
        this.accountRankRepository = accountRankRepository;
        this.accountRankHistoryRepository = accountRankHistoryRepository;
        this.riotMatchRepository = riotMatchRepository;
        this.riotLeagueClient = riotLeagueClient;
        this.riotMatchClient = riotMatchClient;
        this.riotMatchLookupService = riotMatchLookupService;
        this.properties = properties;
    }

    public void syncAllAccounts(Instant now) {
        List<RiotAccount> accounts = riotAccountRepository.findAll();
        riotMatchLookupService.beginRefreshScope();
        int remainingBudget = MAX_MATCH_IMPORTS_PER_PASS;
        boolean matchBudgetExhaustedLogged = false;
        try {
            for (RiotAccount account : accounts) {
                try {
                    ChallengeRegion region = resolveRegion(account);
                    if (region == null) {
                        continue;
                    }

                    riotLeagueClient.findRankedSoloEntry(account.getRiotPuuid(), region)
                            .ifPresent(entry -> upsertRank(account.getRiotPuuid(), now, entry));

                    if (remainingBudget <= 0) {
                        if (!matchBudgetExhaustedLogged) {
                            log.info(
                                    "Leaderboard sync pass hit its {}-match budget; ranks continue, matches resume next pass",
                                    MAX_MATCH_IMPORTS_PER_PASS
                            );
                            matchBudgetExhaustedLogged = true;
                        }
                        continue;
                    }

                    remainingBudget -= syncMatches(account, region, now, remainingBudget);
                } catch (ResponseStatusException exception) {
                    if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                        log.warn("Riot API rate limit while syncing leaderboard accounts; stopping this pass");
                        break;
                    }
                    log.warn("Failed to sync leaderboard account {}: {}", account.getId(), exception.getMessage());
                }
            }
        } finally {
            riotMatchLookupService.endRefreshScope();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void upsertRank(String puuid, Instant now, RiotLeagueEntryDto entry) {
        LeaderboardAccountRank rank = accountRankRepository.findByRiotPuuid(puuid)
                .orElseGet(() -> LeaderboardAccountRank.create(puuid, now, entry.tier(), entry.rank(), entry.leaguePoints()));
        rank.update(now, entry.tier(), entry.rank(), entry.leaguePoints());
        accountRankRepository.save(rank);
        accountRankHistoryRepository.save(
                LeaderboardAccountRankHistory.create(puuid, now, entry.tier(), entry.rank(), entry.leaguePoints())
        );
    }

    /**
     * Resolves and caches which Riot server a tracked account plays on. Accounts aren't
     * region-tagged when created (see {@code RiotAccountService#findOrCreate}), so on an
     * account's first sync this probes one representative platform per distinct continental
     * routing for a single recent ranked solo/duo match id, then decodes the exact platform from
     * its prefix via {@link ChallengeRegion#fromMatchId}. Once persisted, later syncs reuse the
     * cached value with no extra Riot calls.
     *
     * @return the account's region, or {@code null} if none of the probes found any ranked
     *     solo/duo history for this account yet.
     */
    private ChallengeRegion resolveRegion(RiotAccount account) {
        ChallengeRegion cached = account.getRegion();
        if (cached != null) {
            return cached;
        }

        for (ChallengeRegion probe : PROBE_REGIONS) {
            List<String> matchIds = riotMatchClient.getRecentRankedSoloMatchIds(account.getRiotPuuid(), 1, probe);
            if (!matchIds.isEmpty()) {
                ChallengeRegion resolved = ChallengeRegion.fromMatchId(matchIds.get(0));
                account.assignRegion(resolved);
                riotAccountRepository.save(account);
                return resolved;
            }
        }
        return null;
    }

    /**
     * Incrementally imports missing ranked solo/duo season matches for one linked account.
     * Called on Mes statistiques refresh so champion stats can be served from DB (OP.GG-style)
     * without hundreds of live Riot calls per page load.
     */
    public ActivitySyncBatchResult syncAccountForActivity(RiotAccount account, int seasonMatchTotal) {
        ChallengeRegion region = resolveRegion(account);
        if (region == null) {
            return new ActivitySyncBatchResult(0, true, 0);
        }

        int fetchLimit = Math.min(Math.max(seasonMatchTotal, 1), 500);
        String puuid = account.getRiotPuuid();
        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                puuid,
                properties.seasonStartAt().getEpochSecond(),
                null,
                fetchLimit,
                region
        );

        int used = syncMatches(
                account,
                region,
                ACTIVITY_MAX_IMPORTS_PER_REFRESH,
                ACTIVITY_MAX_CATCHUP_MATCHES,
                fetchLimit,
                ACTIVITY_SYNC_BUDGET
        );
        int remainingBudget = Math.max(0, ACTIVITY_SYNC_BUDGET - used);
        if (remainingBudget > 0) {
            backfillMissingCombatStats(puuid, remainingBudget);
        }

        boolean allMatchIdsImported = matchIds.stream()
                .noneMatch(matchId -> accountMatchRepository.findByRiotPuuidAndRiotMatchId(puuid, matchId).isEmpty());
        return new ActivitySyncBatchResult(used, allMatchIdsImported, matchIds.size());
    }

    /**
     * @param availableMatches how many season match ids Riot's match-list API actually returned
     *     for this window at sync time — the ground truth for "how much history exists right now",
     *     as opposed to {@code seasonMatchTotal} (wins+losses from the rank endpoint, which falls
     *     back to a large placeholder when the rank lookup fails). Callers must record exhaustion
     *     against this value, not the caller-supplied total, or a single transient rank-lookup
     *     failure can permanently wedge catch-up (see {@link ActivityAccountBackgroundSyncService}).
     */
    public record ActivitySyncBatchResult(int fetchesUsed, boolean allMatchIdsImported, int availableMatches) {
    }

    /**
     * Fetches Riot match details for rows that predate combat-stat columns and persists KDA/CS.
     */
    public int backfillMissingCombatStats(String puuid, int maxUpdates) {
        if (maxUpdates <= 0) {
            return 0;
        }

        List<String> matchIds = accountMatchRepository.findMatchIdsMissingCombatStatsSince(
                puuid,
                properties.seasonStartAt(),
                org.springframework.data.domain.PageRequest.of(0, maxUpdates)
        );

        int updated = 0;
        for (String matchId : matchIds) {
            Optional<AccountMatch> existing =
                    accountMatchRepository.findByRiotPuuidAndRiotMatchId(puuid, matchId);
            if (existing.isEmpty() || existing.get().hasCombatStats()) {
                continue;
            }

            try {
                if (backfillMatchStats(existing.get(), puuid, matchId)) {
                    updated++;
                }
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn(
                            "Riot rate limit while backfilling combat stats for {} after {} updates",
                            puuid,
                            updated
                    );
                    break;
                }
                log.warn("Unable to backfill combat stats for match {} ({}): {}", matchId, puuid, exception.getReason());
            }
        }
        return updated;
    }

    /**
     * Fetches and imports every ranked solo/duo match since season start that isn't already
     * stored for this account — unlike {@link #syncMatches}, not capped to a small per-pass
     * budget. Meant for a one-off "rattrapage" backfill run against a Riot key with real headroom
     * (see {@code RiotHistoryBackfillService}), where the HTTP-level {@link
     * com.riftchallenge.riot.RiotAppRateLimiter} is what keeps calls within Riot's actual quota
     * instead of an artificial per-pass cap.
     *
     * @return how many new matches were imported.
     */
    public int backfillFullHistory(RiotAccount account) {
        ChallengeRegion region = resolveRegion(account);
        if (region == null) {
            return 0;
        }

        String puuid = account.getRiotPuuid();
        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                puuid,
                FULL_HISTORY_START_EPOCH_SECONDS,
                null,
                Integer.MAX_VALUE,
                region
        );

        int imported = 0;
        for (String matchId : matchIds) {
            if (accountMatchRepository.findByRiotPuuidAndRiotMatchId(puuid, matchId).isPresent()) {
                continue;
            }
            try {
                if (importMatch(puuid, matchId)) {
                    imported++;
                }
            } catch (ResponseStatusException exception) {
                log.warn(
                        "Skipping match {} for account {} during backfill: {}",
                        matchId,
                        account.getId(),
                        exception.getReason()
                );
            }
        }
        return imported;
    }

    /** @return how many match-detail fetches were performed for this account. */
    private int syncMatches(RiotAccount account, ChallengeRegion region, Instant now, int budget) {
        long existingMatches = accountMatchRepository.countByRiotPuuid(account.getRiotPuuid());
        int fetchLimit = existingMatches == 0
                ? Math.min(MAX_CATCHUP_MATCHES, budget)
                : (int) Math.min(100, existingMatches + MAX_NEW_MATCHES_PER_SYNC);
        return syncMatches(
                account,
                region,
                MAX_NEW_MATCHES_PER_SYNC,
                MAX_CATCHUP_MATCHES,
                fetchLimit,
                budget
        );
    }

    /** @return how many match-detail fetches were performed for this account. */
    private int syncMatches(
            RiotAccount account,
            ChallengeRegion region,
            int importLimit,
            int catchUpImportLimit,
            int fetchLimit,
            int budget
    ) {
        String puuid = account.getRiotPuuid();
        long existingMatches = accountMatchRepository.countByRiotPuuid(puuid);
        int effectiveImportLimit = Math.min(existingMatches > 0 ? importLimit : catchUpImportLimit, budget);

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                puuid,
                properties.seasonStartAt().getEpochSecond(),
                null,
                fetchLimit,
                region
        );

        int fetched = 0;
        for (String matchId : matchIds) {
            if (fetched >= effectiveImportLimit || fetched >= budget) {
                break;
            }

            Optional<AccountMatch> existing =
                    accountMatchRepository.findByRiotPuuidAndRiotMatchId(puuid, matchId);
            if (existing.isPresent()) {
                if (!existing.get().hasCombatStats()) {
                    try {
                        if (backfillMatchStats(existing.get(), puuid, matchId)) {
                            fetched++;
                        }
                    } catch (ResponseStatusException exception) {
                        if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            log.warn(
                                    "Riot API rate limit while backfilling leaderboard match stats for account {} after {} updates",
                                    account.getId(),
                                    fetched
                            );
                            break;
                        }
                        throw exception;
                    }
                }
                continue;
            }

            try {
                if (importMatch(puuid, matchId)) {
                    fetched++;
                }
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn(
                            "Riot API rate limit while importing leaderboard matches for account {} after {} new matches",
                            account.getId(),
                            fetched
                    );
                    break;
                }
                throw exception;
            }
        }
        return fetched;
    }

    // Intentionally not @Transactional: this calls out to Riot first (getMatch), which can block
    // for a while under sustained rate limiting (see MAX_MATCH_IMPORTS_PER_PASS above). Each
    // repository read/write below already runs its own short-lived transaction (Spring Data
    // default), so no Hikari connection is held open across the network call.
    boolean importMatch(String puuid, String matchId) {
        RiotMatchDetailDto match = riotMatchLookupService.getMatch(matchId);
        if (match.info().queueId() != RiotMatchClient.RANKED_SOLO_QUEUE_ID) {
            return false;
        }

        Instant gameStart = Instant.ofEpochMilli(match.info().gameStartTimestamp());
        persistMatchIfNeeded(match, gameStart);

        boolean imported = persistParticipantMatchIfNeeded(puuid, match);
        importLinkedCoPlayers(puuid, match);
        return imported;
    }

    private void importLinkedCoPlayers(String primaryPuuid, RiotMatchDetailDto match) {
        List<String> participantPuids = match.info().participants().stream()
                .map(RiotMatchDetailDto.Participant::puuid)
                .toList();
        if (participantPuids.isEmpty()) {
            return;
        }

        Set<String> trackedPuids = new HashSet<>(riotAccountRepository.findPuidsIn(participantPuids));
        trackedPuids.remove(primaryPuuid);
        for (String trackedPuuid : trackedPuids) {
            persistParticipantMatchIfNeeded(trackedPuuid, match);
        }
    }

    private boolean persistParticipantMatchIfNeeded(String puuid, RiotMatchDetailDto match) {
        String matchId = match.metadata().matchId();
        if (accountMatchRepository.findByRiotPuuidAndRiotMatchId(puuid, matchId).isPresent()) {
            return false;
        }

        Optional<ParticipantSnapshot> participant = findParticipantSnapshot(puuid, match);
        if (participant.isEmpty()) {
            return false;
        }

        ParticipantSnapshot snapshot = participant.get();
        try {
            accountMatchRepository.save(AccountMatch.create(
                    puuid,
                    matchId,
                    snapshot.win(),
                    snapshot.championId(),
                    snapshot.championName(),
                    snapshot.kills(),
                    snapshot.deaths(),
                    snapshot.assists(),
                    snapshot.cs(),
                    snapshot.gameDurationSeconds()
            ));
        } catch (DataIntegrityViolationException exception) {
            log.debug("Leaderboard match {} already linked for {}", matchId, puuid);
            return false;
        }
        return true;
    }

    // See importMatch() above: intentionally not @Transactional, same reasoning.
    boolean backfillMatchStats(AccountMatch linkedMatch, String puuid, String matchId) {
        RiotMatchDetailDto match = riotMatchLookupService.getMatch(matchId);
        if (match.info().queueId() != RiotMatchClient.RANKED_SOLO_QUEUE_ID) {
            return false;
        }

        Optional<ParticipantSnapshot> participant = findParticipantSnapshot(puuid, match);
        if (participant.isEmpty()) {
            return false;
        }

        ParticipantSnapshot snapshot = participant.get();
        linkedMatch.backfillCombatStats(
                snapshot.championName(),
                snapshot.kills(),
                snapshot.deaths(),
                snapshot.assists(),
                snapshot.cs(),
                snapshot.gameDurationSeconds()
        );
        accountMatchRepository.save(linkedMatch);
        return true;
    }

    private Optional<ParticipantSnapshot> findParticipantSnapshot(String puuid, RiotMatchDetailDto match) {
        for (RiotMatchDetailDto.Participant participant : match.info().participants()) {
            if (!puuid.equals(participant.puuid())) {
                continue;
            }

            Integer championId = participant.championId() != null && participant.championId() > 0
                    ? participant.championId()
                    : null;
            String championName = participant.championName();
            if (championName != null && championName.isBlank()) {
                championName = null;
            }

            return Optional.of(new ParticipantSnapshot(
                    championId,
                    championName,
                    participant.win(),
                    participant.kills(),
                    participant.deaths(),
                    participant.assists(),
                    participant.totalMinionsKilled() + participant.neutralMinionsKilled(),
                    RiotMatchDurations.normalizeSeconds(match.info().gameDuration())
            ));
        }
        return Optional.empty();
    }

    private record ParticipantSnapshot(
            Integer championId,
            String championName,
            boolean win,
            int kills,
            int deaths,
            int assists,
            int cs,
            long gameDurationSeconds
    ) {
    }

    private void persistMatchIfNeeded(RiotMatchDetailDto match, Instant gameStart) {
        String matchId = match.metadata().matchId();
        if (riotMatchRepository.existsByRiotMatchId(matchId)) {
            return;
        }

        try {
            riotMatchRepository.saveAndFlush(RiotMatch.create(matchId, match.info().queueId(), gameStart));
        } catch (DataIntegrityViolationException exception) {
            log.debug("Riot match {} already persisted by another sync", matchId);
        }
    }
}
