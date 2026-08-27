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
 * <p>Tracked accounts aren't region-tagged yet; assumes EUW,
 * same simplification used everywhere else a linked account is queried outside a challenge.
 */
@Service
public class LeaderboardAccountSyncService {

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
    private final LeaderboardAccountMatchRepository accountMatchRepository;
    private final LeaderboardAccountRankRepository accountRankRepository;
    private final RiotMatchRepository riotMatchRepository;
    private final RiotLeagueClient riotLeagueClient;
    private final RiotMatchClient riotMatchClient;
    private final RiotMatchLookupService riotMatchLookupService;
    private final LeaderboardProperties properties;

    public LeaderboardAccountSyncService(
            RiotAccountRepository riotAccountRepository,
            LeaderboardAccountMatchRepository accountMatchRepository,
            LeaderboardAccountRankRepository accountRankRepository,
            RiotMatchRepository riotMatchRepository,
            RiotLeagueClient riotLeagueClient,
            RiotMatchClient riotMatchClient,
            RiotMatchLookupService riotMatchLookupService,
            LeaderboardProperties properties
    ) {
        this.riotAccountRepository = riotAccountRepository;
        this.accountMatchRepository = accountMatchRepository;
        this.accountRankRepository = accountRankRepository;
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
                    riotLeagueClient.findRankedSoloEntry(account.getRiotPuuid(), ChallengeRegion.EUW)
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

                    remainingBudget -= syncMatches(account, now, remainingBudget);
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
    }

    /**
     * Incrementally imports missing ranked solo/duo season matches for one linked account.
     * Called on Mes statistiques refresh so champion stats can be served from DB (OP.GG-style)
     * without hundreds of live Riot calls per page load.
     */
    public ActivitySyncBatchResult syncAccountForActivity(RiotAccount account, int seasonMatchTotal) {
        int fetchLimit = Math.min(Math.max(seasonMatchTotal, 1), 500);
        String puuid = account.getRiotPuuid();
        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                puuid,
                properties.seasonStartAt().getEpochSecond(),
                null,
                fetchLimit,
                ChallengeRegion.EUW
        );

        int used = syncMatches(
                account,
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
        return new ActivitySyncBatchResult(used, allMatchIdsImported);
    }

    public record ActivitySyncBatchResult(int fetchesUsed, boolean allMatchIdsImported) {
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
            Optional<LeaderboardAccountMatch> existing =
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
        String puuid = account.getRiotPuuid();
        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                puuid,
                properties.seasonStartAt().getEpochSecond(),
                null,
                Integer.MAX_VALUE,
                ChallengeRegion.EUW
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
    private int syncMatches(RiotAccount account, Instant now, int budget) {
        long existingMatches = accountMatchRepository.countByRiotPuuid(account.getRiotPuuid());
        int fetchLimit = existingMatches == 0
                ? Math.min(MAX_CATCHUP_MATCHES, budget)
                : (int) Math.min(100, existingMatches + MAX_NEW_MATCHES_PER_SYNC);
        return syncMatches(
                account,
                MAX_NEW_MATCHES_PER_SYNC,
                MAX_CATCHUP_MATCHES,
                fetchLimit,
                budget
        );
    }

    /** @return how many match-detail fetches were performed for this account. */
    private int syncMatches(
            RiotAccount account,
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
                ChallengeRegion.EUW
        );

        int fetched = 0;
        for (String matchId : matchIds) {
            if (fetched >= effectiveImportLimit || fetched >= budget) {
                break;
            }

            Optional<LeaderboardAccountMatch> existing =
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
            accountMatchRepository.save(LeaderboardAccountMatch.create(
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean backfillMatchStats(LeaderboardAccountMatch linkedMatch, String puuid, String matchId) {
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
