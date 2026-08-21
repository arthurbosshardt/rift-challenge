package com.riftchallenge.leaderboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import com.riftchallenge.synchronization.RiotMatchRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the independent leaderboard sync's key invariants: only ranked solo/duo (queue 420)
 * counts, a match already linked to the account is never re-imported, and the per-sync import
 * cap holds. Mirrors ChallengeParticipantSyncServiceTest but keyed by account, not challenge.
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardAccountSyncServiceTest {

    private static final Instant SEASON_START = Instant.parse("2026-07-29T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final String PUUID = "puuid-1";

    @Mock
    private UserRiotAccountRepository userRiotAccountRepository;

    @Mock
    private LeaderboardAccountMatchRepository accountMatchRepository;

    @Mock
    private LeaderboardAccountRankRepository accountRankRepository;

    @Mock
    private RiotMatchRepository riotMatchRepository;

    @Mock
    private RiotLeagueClient riotLeagueClient;

    @Mock
    private RiotMatchClient riotMatchClient;

    @Mock
    private RiotMatchLookupService riotMatchLookupService;

    private LeaderboardAccountSyncService service;
    private UserRiotAccount account;

    @BeforeEach
    void setUp() {
        service = new LeaderboardAccountSyncService(
                userRiotAccountRepository,
                accountMatchRepository,
                accountRankRepository,
                riotMatchRepository,
                riotLeagueClient,
                riotMatchClient,
                riotMatchLookupService,
                new LeaderboardProperties(SEASON_START, "admin@example.com")
        );

        account = UserRiotAccount.create(UUID.randomUUID(), new RiotAccountDto(PUUID, "Player", "EUW"));
        when(userRiotAccountRepository.findAll()).thenReturn(List.of(account));
        when(riotLeagueClient.findRankedSoloEntry(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void syncAllAccounts_importsMatch_onRankedSoloQueue() {
        String matchId = "EUW1_111";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        when(riotMatchLookupService.getMatch(matchId)).thenReturn(matchDetail(matchId, 420, true, 99));

        service.syncAllAccounts(NOW);

        verify(accountMatchRepository, times(1)).save(any());
    }

    @Test
    void syncAllAccounts_skipsMatch_offRankedSoloQueue() {
        String matchId = "EUW1_222";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        // Flex queue (440), not ranked solo/duo (420) — must never count toward the leaderboard.
        when(riotMatchLookupService.getMatch(matchId)).thenReturn(matchDetail(matchId, 440, true, 99));

        service.syncAllAccounts(NOW);

        verify(accountMatchRepository, never()).save(any());
    }

    @Test
    void syncAllAccounts_dedupsAlreadyLinkedMatch_doesNotReimport() {
        String matchId = "EUW1_333";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        when(accountMatchRepository.existsByRiotPuuidAndRiotMatchId(PUUID, matchId)).thenReturn(true);

        service.syncAllAccounts(NOW);

        verify(riotMatchLookupService, never()).getMatch(anyString());
        verify(accountMatchRepository, never()).save(any());
    }

    @Test
    void syncAllAccounts_stopsImportingAtPerSyncCap() {
        when(accountMatchRepository.countByRiotPuuid(PUUID)).thenReturn(1L);

        List<String> matchIds = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> "EUW1_" + (600 + i))
                .toList();
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(matchIds);
        when(riotMatchLookupService.getMatch(anyString()))
                .thenAnswer(invocation -> matchDetail(invocation.getArgument(0), 420, true, 99));

        service.syncAllAccounts(NOW);

        verify(accountMatchRepository, times(LeaderboardAccountSyncService.MAX_NEW_MATCHES_PER_SYNC)).save(any());
    }

    private static RiotMatchDetailDto matchDetail(String matchId, int queueId, boolean win, int championId) {
        return new RiotMatchDetailDto(
                new RiotMatchDetailDto.Metadata(matchId),
                new RiotMatchDetailDto.Info(
                        NOW.toEpochMilli(),
                        queueId,
                        List.of(new RiotMatchDetailDto.Participant(PUUID, win, 4586, championId, "Champ"))
                )
        );
    }
}
