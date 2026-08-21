package com.riftchallenge.leaderboard;

import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import com.riftchallenge.synchronization.RiotMatch;
import com.riftchallenge.synchronization.RiotMatchRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
 * <p>Linked accounts aren't region-tagged yet (see {@code UserRiotAccountService}); assumes EUW,
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

    private static final Logger log = LoggerFactory.getLogger(LeaderboardAccountSyncService.class);

    private final UserRiotAccountRepository userRiotAccountRepository;
    private final LeaderboardAccountMatchRepository accountMatchRepository;
    private final LeaderboardAccountRankRepository accountRankRepository;
    private final RiotMatchRepository riotMatchRepository;
    private final RiotLeagueClient riotLeagueClient;
    private final RiotMatchClient riotMatchClient;
    private final RiotMatchLookupService riotMatchLookupService;
    private final LeaderboardProperties properties;

    public LeaderboardAccountSyncService(
            UserRiotAccountRepository userRiotAccountRepository,
            LeaderboardAccountMatchRepository accountMatchRepository,
            LeaderboardAccountRankRepository accountRankRepository,
            RiotMatchRepository riotMatchRepository,
            RiotLeagueClient riotLeagueClient,
            RiotMatchClient riotMatchClient,
            RiotMatchLookupService riotMatchLookupService,
            LeaderboardProperties properties
    ) {
        this.userRiotAccountRepository = userRiotAccountRepository;
        this.accountMatchRepository = accountMatchRepository;
        this.accountRankRepository = accountRankRepository;
        this.riotMatchRepository = riotMatchRepository;
        this.riotLeagueClient = riotLeagueClient;
        this.riotMatchClient = riotMatchClient;
        this.riotMatchLookupService = riotMatchLookupService;
        this.properties = properties;
    }

    public void syncAllAccounts(Instant now) {
        List<UserRiotAccount> accounts = userRiotAccountRepository.findAll();
        riotMatchLookupService.beginRefreshScope();
        int remainingBudget = MAX_MATCH_IMPORTS_PER_PASS;
        boolean matchBudgetExhaustedLogged = false;
        try {
            for (UserRiotAccount account : accounts) {
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

    /** @return how many new matches were imported for this account, so the caller can track its pass-wide budget. */
    private int syncMatches(UserRiotAccount account, Instant now, int budget) {
        String puuid = account.getRiotPuuid();
        long existingMatches = accountMatchRepository.countByRiotPuuid(puuid);
        int importLimit = Math.min(existingMatches > 0 ? MAX_NEW_MATCHES_PER_SYNC : MAX_CATCHUP_MATCHES, budget);
        int fetchLimit = existingMatches == 0 ? importLimit : (int) Math.min(100, existingMatches + importLimit);

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                puuid,
                properties.seasonStartAt().getEpochSecond(),
                null,
                fetchLimit,
                ChallengeRegion.EUW
        );

        int imported = 0;
        for (String matchId : matchIds) {
            if (imported >= importLimit) {
                break;
            }
            if (accountMatchRepository.existsByRiotPuuidAndRiotMatchId(puuid, matchId)) {
                continue;
            }

            try {
                if (importMatch(puuid, matchId)) {
                    imported++;
                }
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn(
                            "Riot API rate limit while importing leaderboard matches for account {} after {} new matches",
                            account.getId(),
                            imported
                    );
                    break;
                }
                throw exception;
            }
        }
        return imported;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    boolean importMatch(String puuid, String matchId) {
        RiotMatchDetailDto match = riotMatchLookupService.getMatch(matchId);
        if (match.info().queueId() != RiotMatchClient.RANKED_SOLO_QUEUE_ID) {
            return false;
        }

        Instant gameStart = Instant.ofEpochMilli(match.info().gameStartTimestamp());
        persistMatchIfNeeded(match, gameStart);

        boolean win = false;
        Integer championId = null;
        for (RiotMatchDetailDto.Participant participant : match.info().participants()) {
            if (!puuid.equals(participant.puuid())) {
                continue;
            }
            win = participant.win();
            championId = participant.championId();
            break;
        }

        try {
            accountMatchRepository.save(LeaderboardAccountMatch.create(puuid, matchId, win, championId));
        } catch (DataIntegrityViolationException exception) {
            // Another sync pass (e.g. an overlapping admin refresh) already linked this match to
            // this account between our existence check and this insert — already have it either way.
            log.debug("Leaderboard match {} already linked for {}", matchId, puuid);
            return false;
        }
        return true;
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
