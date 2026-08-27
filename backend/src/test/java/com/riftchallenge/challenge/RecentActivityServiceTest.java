package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riftchallenge.TestRiotAccounts;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.account.RiotAccountService;
import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.challenge.dto.AccountRecentGamesResponse;
import com.riftchallenge.leaderboard.AccountMatchRepository;
import com.riftchallenge.leaderboard.AccountMatchRepository.SeasonActivityRow;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecentActivityServiceTest {

    private static final Instant SEASON_START = Instant.parse("2026-01-08T12:00:00Z");
    private static final String PUUID = "puuid-1";

    @Mock
    private UserRiotAccountRepository userRiotAccountRepository;

    @Mock
    private RiotAccountRepository riotAccountRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private RiotAccountService riotAccountService;

    @Mock
    private AccountMatchRepository accountMatchRepository;

    @Mock
    private ActivityAccountBackgroundSyncService backgroundSyncService;

    @Mock
    private RiotLeagueClient riotLeagueClient;

    private RecentActivityService service;

    @BeforeEach
    void setUp() {
        service = new RecentActivityService(
                userRiotAccountRepository,
                riotAccountRepository,
                participantRepository,
                riotAccountService,
                accountMatchRepository,
                backgroundSyncService,
                riotLeagueClient,
                new ChampionIconUrlService(new ObjectMapper()),
                new LeaderboardProperties(SEASON_START, "admin@example.com")
        );
    }

    @Test
    void listRecentGames_noLinkedAccount_returnsEmptyList() {
        UUID userId = UUID.randomUUID();
        when(userRiotAccountRepository.findByUserId(userId)).thenReturn(Optional.empty());

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result).isEmpty();
    }

    @Test
    void listRecentGames_showsChampionsWhileSeasonSyncInProgress() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = linkedAccount(userId, PUUID);
        when(userRiotAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(riotLeagueClient.findRankedSoloEntry(PUUID, ChallengeRegion.EUW))
                .thenReturn(Optional.of(new RiotLeagueEntryDto("RANKED_SOLO_5x5", "GOLD", "II", 55, 72, 65)));
        when(backgroundSyncService.isSeasonHistoryExhausted(account.getRiotAccount().getId(), 137)).thenReturn(false);
        when(accountMatchRepository.findSeasonActivitySince(PUUID, SEASON_START)).thenReturn(
                java.util.stream.IntStream.range(0, 100)
                        .mapToObj(i -> seasonRow(
                                "EUW1_" + i,
                                i % 2 == 0,
                                63,
                                "Brand",
                                6,
                                4,
                                8,
                                153,
                                1_800L,
                                Instant.parse("2026-08-19T12:00:00Z").minusSeconds(i)
                        ))
                        .toList()
        );

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result.get(0).seasonSyncComplete()).isFalse();
        assertThat(result.get(0).seasonSyncInProgress()).isTrue();
        assertThat(result.get(0).syncedGames()).isEqualTo(100);
        assertThat(result.get(0).seasonGames()).isEqualTo(137);
        assertThat(result.get(0).champions()).isNotEmpty();
        assertThat(result.get(0).champions().getFirst().games()).isEqualTo(100);
        verify(backgroundSyncService).scheduleSyncIfIdle(account.getRiotAccount(), 137, 100);
    }

    @Test
    void listRecentGames_marksSyncInProgressWhenCatchUpNotActiveYet() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = linkedAccount(userId, PUUID);
        when(userRiotAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(riotLeagueClient.findRankedSoloEntry(PUUID, ChallengeRegion.EUW))
                .thenReturn(Optional.of(new RiotLeagueEntryDto("RANKED_SOLO_5x5", "GOLD", "II", 55, 2, 1)));
        when(backgroundSyncService.isSeasonHistoryExhausted(account.getRiotAccount().getId(), 3)).thenReturn(false);
        when(accountMatchRepository.findSeasonActivitySince(PUUID, SEASON_START)).thenReturn(List.of());

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result.get(0).seasonSyncComplete()).isFalse();
        assertThat(result.get(0).seasonSyncInProgress()).isTrue();
        assertThat(result.get(0).champions()).isEmpty();
        verify(backgroundSyncService).scheduleSyncIfIdle(account.getRiotAccount(), 3, 0);
    }

    @Test
    void listRecentGames_marksCompleteWhenSeasonHistoryExhausted() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = linkedAccount(userId, PUUID);
        account.markActivitySeasonHistoryExhausted(500);
        when(userRiotAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(accountMatchRepository.findSeasonActivitySince(PUUID, SEASON_START)).thenReturn(
                java.util.stream.IntStream.range(0, 125)
                        .mapToObj(i -> seasonRow(
                                "EUW1_" + i,
                                i % 2 == 0,
                                63,
                                "Brand",
                                6,
                                4,
                                8,
                                153,
                                1_800L,
                                Instant.parse("2026-08-19T12:00:00Z").minusSeconds(i)
                        ))
                        .toList()
        );

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result.get(0).seasonSyncComplete()).isTrue();
        assertThat(result.get(0).seasonSyncInProgress()).isFalse();
        assertThat(result.get(0).syncedGames()).isEqualTo(125);
        assertThat(result.get(0).seasonGames()).isEqualTo(125);
        assertThat(result.get(0).champions()).isNotEmpty();
    }

    @Test
    void listRecentGames_showsChampionsWhenSeasonSyncComplete() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = linkedAccount(userId, PUUID);
        when(userRiotAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(riotLeagueClient.findRankedSoloEntry(PUUID, ChallengeRegion.EUW))
                .thenReturn(Optional.of(new RiotLeagueEntryDto("RANKED_SOLO_5x5", "GOLD", "II", 55, 2, 1)));
        when(accountMatchRepository.findSeasonActivitySince(PUUID, SEASON_START)).thenReturn(List.of(
                seasonRow("EUW1_2", true, 55, "Katarina", 4, 2, 10, 120, 1_800L, Instant.parse("2026-08-20T12:00:00Z")),
                seasonRow("EUW1_1", false, 63, "Brand", 6, 4, 8, 153, 1_800L, Instant.parse("2026-08-19T12:00:00Z")),
                seasonRow("EUW1_0", true, 63, "Brand", 2, 1, 4, 100, 1_800L, Instant.parse("2026-08-18T12:00:00Z"))
        ));

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result.get(0).seasonSyncComplete()).isTrue();
        assertThat(result.get(0).syncedGames()).isEqualTo(3);
        assertThat(result.get(0).seasonGames()).isEqualTo(3);
        assertThat(result.get(0).champions()).hasSize(3);
        assertThat(result.get(0).champions().getFirst().games()).isEqualTo(3);
        assertThat(result.get(0).champions().getFirst().wins()).isEqualTo(2);
        verify(backgroundSyncService).scheduleSyncIfIdle(account.getRiotAccount(), 3, 3);
    }

    @Test
    void listRecentGames_rowsWithoutCombatStats_stillCountGamesButSkipKdaAverages() {
        UUID userId = UUID.randomUUID();
        UserRiotAccount account = linkedAccount(userId, PUUID);
        when(userRiotAccountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(riotLeagueClient.findRankedSoloEntry(PUUID, ChallengeRegion.EUW)).thenReturn(Optional.empty());
        when(accountMatchRepository.findSeasonActivitySince(PUUID, SEASON_START)).thenReturn(List.of(
                seasonRow("EUW1_1", true, 63, "Brand", null, null, null, null, null, Instant.parse("2026-08-19T12:00:00Z"))
        ));

        List<AccountRecentGamesResponse> result = service.listRecentGames(userId);

        assertThat(result.get(0).seasonSyncComplete()).isFalse();
        assertThat(result.get(0).champions()).isNotEmpty();
        verify(backgroundSyncService).scheduleSyncIfIdle(account.getRiotAccount(), 500, 1);
    }

    @Test
    void getActivityForRiotId_unknownRiotId_returnsEmpty() {
        when(riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase("Ghost", "EUW"))
                .thenReturn(Optional.empty());
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("Ghost", "EUW"))
                .thenReturn(Optional.empty());

        Optional<AccountRecentGamesResponse> result = service.getActivityForRiotId("Ghost", "EUW");

        assertThat(result).isEmpty();
    }

    /**
     * A challenge participant isn't necessarily backed by a RiotAccount row yet — viewing their
     * activity should register one on the fly rather than 404, independent of whether
     * PlayerLookupService.resolve() was called first.
     */
    @Test
    void getActivityForRiotId_participantOnlyPlayer_registersAccountAndReturnsActivity() {
        com.riftchallenge.account.RiotAccount riotAccount = TestRiotAccounts.riotAccount(PUUID, "NoLink", "NA1");
        ChallengeParticipant participant = ChallengeParticipant.create(
                UUID.randomUUID(), new com.riftchallenge.riot.dto.RiotAccountDto(PUUID, "NoLink", "NA1")
        );
        when(riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase("NoLink", "NA1"))
                .thenReturn(Optional.empty());
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("NoLink", "NA1"))
                .thenReturn(Optional.of(participant));
        when(riotAccountService.findOrCreate(
                new RiotAccountDto(PUUID, "NoLink", "NA1"),
                participant.getProfileIconId()
        )).thenReturn(riotAccount);
        when(riotLeagueClient.findRankedSoloEntry(PUUID, ChallengeRegion.EUW)).thenReturn(Optional.empty());
        when(backgroundSyncService.isSeasonHistoryExhausted(riotAccount.getId(), 500)).thenReturn(false);
        when(accountMatchRepository.findSeasonActivitySince(PUUID, SEASON_START)).thenReturn(List.of());

        Optional<AccountRecentGamesResponse> result = service.getActivityForRiotId("NoLink", "NA1");

        assertThat(result).isPresent();
        assertThat(result.get().accountId()).isEqualTo(riotAccount.getId());
        verify(riotAccountService).findOrCreate(new RiotAccountDto(PUUID, "NoLink", "NA1"), participant.getProfileIconId());
    }

    @Test
    void getActivityForRiotId_knownRiotId_returnsActivityKeyedByRiotAccountId() {
        com.riftchallenge.account.RiotAccount riotAccount = TestRiotAccounts.riotAccount(PUUID, "Tanor", "EUW");
        when(riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase("Tanor", "EUW"))
                .thenReturn(Optional.of(riotAccount));
        when(riotLeagueClient.findRankedSoloEntry(PUUID, ChallengeRegion.EUW)).thenReturn(Optional.empty());
        when(backgroundSyncService.isSeasonHistoryExhausted(riotAccount.getId(), 500)).thenReturn(false);
        when(accountMatchRepository.findSeasonActivitySince(PUUID, SEASON_START)).thenReturn(List.of());

        Optional<AccountRecentGamesResponse> result = service.getActivityForRiotId("Tanor", "EUW");

        assertThat(result).isPresent();
        assertThat(result.get().accountId()).isEqualTo(riotAccount.getId());
        assertThat(result.get().gameName()).isEqualTo("Tanor");
    }

    private static UserRiotAccount linkedAccount(UUID userId, String puuid) {
        return TestRiotAccounts.linkedAccount(userId, puuid, "Tanor", "EUW");
    }

    private static SeasonActivityRow seasonRow(
            String matchId,
            boolean win,
            int championId,
            String championName,
            Integer kills,
            Integer deaths,
            Integer assists,
            Integer cs,
            Long durationSeconds,
            Instant playedAt
    ) {
        return new SeasonActivityRow() {
            @Override
            public String getMatchId() {
                return matchId;
            }

            @Override
            public boolean isWin() {
                return win;
            }

            @Override
            public Integer getChampionId() {
                return championId;
            }

            @Override
            public String getChampionName() {
                return championName;
            }

            @Override
            public Integer getKills() {
                return kills;
            }

            @Override
            public Integer getDeaths() {
                return deaths;
            }

            @Override
            public Integer getAssists() {
                return assists;
            }

            @Override
            public Integer getCs() {
                return cs;
            }

            @Override
            public Long getGameDurationSeconds() {
                return durationSeconds;
            }

            @Override
            public Instant getGameStart() {
                return playedAt;
            }
        };
    }
}
