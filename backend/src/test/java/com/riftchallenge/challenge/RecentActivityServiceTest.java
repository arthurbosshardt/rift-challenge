package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.challenge.dto.AccountRecentGamesResponse;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RecentActivityServiceTest {

    @Mock
    private UserRiotAccountRepository userRiotAccountRepository;

    @Mock
    private RiotMatchClient riotMatchClient;

    @Mock
    private RiotMatchLookupService riotMatchLookupService;

    @Mock
    private RiotLeagueClient riotLeagueClient;

    private RecentActivityService service;

    @BeforeEach
    void setUp() {
        service = new RecentActivityService(
                userRiotAccountRepository,
                riotMatchClient,
                riotMatchLookupService,
                riotLeagueClient,
                new ChampionIconUrlService(new ObjectMapper())
        );
    }

    @Test
    void listRecentGames_noLinkedAccounts_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of());

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result).isEmpty();
        verify(riotMatchLookupService).beginRefreshScope();
        verify(riotMatchLookupService).endRefreshScope();
    }

    @Test
    void listRecentGames_matchMissingRequestedParticipant_skipsGameInsteadOfFabricatingLoss() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = linkedAccount(userId, "puuid-1");
        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(account));
        when(riotLeagueClient.findRankedSoloEntry("puuid-1")).thenReturn(Optional.empty());
        when(riotMatchClient.getRecentRankedSoloMatchIds("puuid-1", RecentActivityService.RECENT_GAMES_PER_ACCOUNT))
                .thenReturn(List.of("EUW1_1"));
        when(riotMatchLookupService.getMatch("EUW1_1")).thenReturn(
                matchWithParticipants(new RiotMatchDetailDto.Participant("some-other-puuid", true, null, 122, "Darius"))
        );

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).games()).isEmpty();
    }

    @Test
    void listRecentGames_championIdZero_mapsToNullInsteadOfBogusIcon() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = linkedAccount(userId, "puuid-1");
        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(account));
        when(riotLeagueClient.findRankedSoloEntry("puuid-1")).thenReturn(Optional.empty());
        when(riotMatchClient.getRecentRankedSoloMatchIds("puuid-1", RecentActivityService.RECENT_GAMES_PER_ACCOUNT))
                .thenReturn(List.of("EUW1_1"));
        when(riotMatchLookupService.getMatch("EUW1_1")).thenReturn(
                matchWithParticipants(new RiotMatchDetailDto.Participant("puuid-1", false, null, 0, ""))
        );

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result.get(0).games()).hasSize(1);
        assertThat(result.get(0).games().get(0).championId()).isNull();
        assertThat(result.get(0).games().get(0).championIconUrl()).isNull();
    }

    @Test
    void listRecentGames_oneMatchDetailFetchFails_skipsItButKeepsTheOthers() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = linkedAccount(userId, "puuid-1");
        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(account));
        when(riotLeagueClient.findRankedSoloEntry("puuid-1")).thenReturn(Optional.empty());
        when(riotMatchClient.getRecentRankedSoloMatchIds("puuid-1", RecentActivityService.RECENT_GAMES_PER_ACCOUNT))
                .thenReturn(List.of("EUW1_1", "EUW1_2"));
        when(riotMatchLookupService.getMatch("EUW1_1"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Riot match not found"));
        when(riotMatchLookupService.getMatch("EUW1_2")).thenReturn(
                matchWithParticipants(new RiotMatchDetailDto.Participant("puuid-1", true, null, 55, "Katarina"))
        );

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result.get(0).games()).hasSize(1);
        assertThat(result.get(0).games().get(0).championId()).isEqualTo(55);
    }

    @Test
    void listRecentGames_matchIdLookupFailsForOneAccount_stillReturnsOtherAccounts() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount broken = linkedAccount(userId, "puuid-1");
        UserRiotAccount healthy = linkedAccount(userId, "puuid-2");
        when(userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId)).thenReturn(List.of(broken, healthy));
        when(riotLeagueClient.findRankedSoloEntry("puuid-1")).thenReturn(Optional.empty());
        when(riotLeagueClient.findRankedSoloEntry("puuid-2")).thenReturn(Optional.empty());
        when(riotMatchClient.getRecentRankedSoloMatchIds("puuid-1", RecentActivityService.RECENT_GAMES_PER_ACCOUNT))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Riot API request failed"));
        when(riotMatchClient.getRecentRankedSoloMatchIds("puuid-2", RecentActivityService.RECENT_GAMES_PER_ACCOUNT))
                .thenReturn(List.of());

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).games()).isEmpty();
        assertThat(result.get(1).games()).isEmpty();
    }

    private static UserRiotAccount linkedAccount(UUID userId, String puuid) {
        return UserRiotAccount.create(userId, new RiotAccountDto(puuid, "Tanor", "EUW"), null, true);
    }

    private static RiotMatchDetailDto matchWithParticipants(RiotMatchDetailDto.Participant... participants) {
        return new RiotMatchDetailDto(
                new RiotMatchDetailDto.Metadata("EUW1_1"),
                new RiotMatchDetailDto.Info(1_700_000_000_000L, 420, List.of(participants))
        );
    }
}
