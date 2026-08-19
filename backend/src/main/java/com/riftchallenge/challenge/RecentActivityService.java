package com.riftchallenge.challenge;

import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.challenge.dto.AccountRecentGamesResponse;
import com.riftchallenge.challenge.dto.RecentGameResponse;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecentActivityService {

    static final int RECENT_GAMES_PER_ACCOUNT = 20;

    private static final Logger log = LoggerFactory.getLogger(RecentActivityService.class);
    private static final Comparator<UserRiotAccount> PRIMARY_ACCOUNT_FIRST =
            Comparator.comparing(UserRiotAccount::isPrimaryAccount).reversed()
                    .thenComparing(UserRiotAccount::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    private final UserRiotAccountRepository userRiotAccountRepository;
    private final RiotMatchClient riotMatchClient;
    private final RiotMatchLookupService riotMatchLookupService;
    private final RiotLeagueClient riotLeagueClient;
    private final ChampionIconUrlService championIconUrlService;

    public RecentActivityService(
            UserRiotAccountRepository userRiotAccountRepository,
            RiotMatchClient riotMatchClient,
            RiotMatchLookupService riotMatchLookupService,
            RiotLeagueClient riotLeagueClient,
            ChampionIconUrlService championIconUrlService
    ) {
        this.userRiotAccountRepository = userRiotAccountRepository;
        this.riotMatchClient = riotMatchClient;
        this.riotMatchLookupService = riotMatchLookupService;
        this.riotLeagueClient = riotLeagueClient;
        this.championIconUrlService = championIconUrlService;
    }

    public List<AccountRecentGamesResponse> listRecentGames(UUID userId) {
        var accounts = userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .sorted(PRIMARY_ACCOUNT_FIRST)
                .toList();

        riotMatchLookupService.beginRefreshScope();
        try {
            return accounts.stream().map(this::toAccountResponse).toList();
        } finally {
            riotMatchLookupService.endRefreshScope();
        }
    }

    private AccountRecentGamesResponse toAccountResponse(UserRiotAccount account) {
        Optional<RiotLeagueEntryDto> currentRank = fetchCurrentRank(account);
        return new AccountRecentGamesResponse(
                account.getId(),
                account.getRiotGameName(),
                account.getRiotTagLine(),
                account.getProfileIconId(),
                account.isPrimaryAccount(),
                currentRank.map(RiotLeagueEntryDto::tier).orElse(null),
                currentRank.map(RiotLeagueEntryDto::rank).orElse(null),
                currentRank.map(RiotLeagueEntryDto::leaguePoints).orElse(null),
                currentRank.map(RiotLeagueEntryDto::wins).orElse(null),
                currentRank.map(RiotLeagueEntryDto::losses).orElse(null),
                fetchRecentGames(account)
        );
    }

    private Optional<RiotLeagueEntryDto> fetchCurrentRank(UserRiotAccount account) {
        try {
            return riotLeagueClient.findRankedSoloEntry(account.getRiotPuuid());
        } catch (ResponseStatusException exception) {
            log.warn(
                    "Unable to fetch current rank for account {}: {}",
                    account.getId(),
                    exception.getReason()
            );
            return Optional.empty();
        }
    }

    private List<RecentGameResponse> fetchRecentGames(UserRiotAccount account) {
        List<String> matchIds;
        try {
            matchIds = riotMatchClient.getRecentRankedSoloMatchIds(account.getRiotPuuid(), RECENT_GAMES_PER_ACCOUNT);
        } catch (ResponseStatusException exception) {
            log.warn(
                    "Unable to fetch recent match ids for account {}: {}",
                    account.getId(),
                    exception.getReason()
            );
            return List.of();
        }

        List<RecentGameResponse> games = new ArrayList<>();
        for (String matchId : matchIds) {
            try {
                toRecentGameResponse(account, matchId, riotMatchLookupService.getMatch(matchId))
                        .ifPresent(games::add);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn(
                            "Riot rate limit reached while fetching recent games for account {} after {} matches",
                            account.getId(),
                            games.size()
                    );
                    break;
                }
                log.warn(
                        "Unable to fetch detail for match {} (account {}): {}",
                        matchId,
                        account.getId(),
                        exception.getReason()
                );
            }
        }
        return games;
    }

    private Optional<RecentGameResponse> toRecentGameResponse(
            UserRiotAccount account,
            String matchId,
            RiotMatchDetailDto match
    ) {
        for (RiotMatchDetailDto.Participant participant : match.info().participants()) {
            if (!account.getRiotPuuid().equals(participant.puuid())) {
                continue;
            }

            Integer championId = participant.championId() != null && participant.championId() > 0
                    ? participant.championId()
                    : null;

            return Optional.of(new RecentGameResponse(
                    matchId,
                    account.getRiotGameName(),
                    account.getRiotTagLine(),
                    championId,
                    championIconUrlService.buildApiPath(championId),
                    participant.win(),
                    Instant.ofEpochMilli(match.info().gameStartTimestamp())
            ));
        }

        log.warn("Match {} did not include a participant for account {}; skipping", matchId, account.getId());
        return Optional.empty();
    }
}
