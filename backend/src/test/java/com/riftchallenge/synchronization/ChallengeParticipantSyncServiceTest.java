package com.riftchallenge.synchronization;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeDataSyncService;
import com.riftchallenge.challenge.ChallengeType;
import com.riftchallenge.challenge.ParticipantProfileService;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Covers the highest-risk behaviour of the Riot sync pipeline: only ranked solo/duo
 * (queue 420) counts, only matches inside [startAt, endAt) count, a match already
 * linked to the participant is never re-imported, and the per-refresh import cap holds.
 * Historical/estimated-rank branches are exercised elsewhere (RankReplayServiceTest,
 * DuoEligibilityServiceTest) and are neutralized here via an empty league lookup.
 */
@ExtendWith(MockitoExtension.class)
class ChallengeParticipantSyncServiceTest {

    private static final Instant START_AT = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-08-10T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
    private static final String PUUID = "puuid-1";

    @Mock
    private RankSnapshotRepository rankSnapshotRepository;

    @Mock
    private RiotMatchRepository riotMatchRepository;

    @Mock
    private ChallengeParticipantMatchRepository participantMatchRepository;

    @Mock
    private RiotLeagueClient riotLeagueClient;

    @Mock
    private RiotMatchClient riotMatchClient;

    @Mock
    private RiotMatchLookupService riotMatchLookupService;

    @Mock
    private ParticipantProfileService participantProfileService;

    @Mock
    private ParticipantMatchChampionBackfillService championBackfillService;

    @Mock
    private HistoricalRankSnapshotService historicalRankSnapshotService;

    @Mock
    private ChallengeDataSyncService dataSyncService;

    private ChallengeParticipantSyncService service;
    private Challenge challenge;
    private ChallengeParticipant participant;

    @BeforeEach
    void setUp() {
        service = new ChallengeParticipantSyncService(
                rankSnapshotRepository,
                riotMatchRepository,
                participantMatchRepository,
                riotLeagueClient,
                riotMatchClient,
                riotMatchLookupService,
                participantProfileService,
                championBackfillService,
                historicalRankSnapshotService,
                dataSyncService
        );

        challenge = Challenge.create(
                java.util.UUID.randomUUID(), "Test", ChallengeType.SOLOQ, START_AT, END_AT
        );
        participant = ChallengeParticipant.create(challenge.getId(), new RiotAccountDto(PUUID, "Player", "EUW"));

        // Not-finished challenge: keeps the historical/estimated-rank branch a no-op
        // so these tests stay focused on match-window sync behaviour.
        when(riotLeagueClient.findRankedSoloEntry(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void syncParticipant_importsMatch_withinWindowOnRankedSoloQueue() {
        String matchId = "EUW1_111";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        when(riotMatchLookupService.getMatch(matchId))
                .thenReturn(matchDetail(matchId, START_AT.plusSeconds(3600), 420, true, 99));

        service.syncParticipant(challenge, participant, NOW);

        verify(participantMatchRepository, times(1)).save(any());
    }

    @Test
    void syncParticipant_skipsMatch_beforeChallengeStart() {
        String matchId = "EUW1_222";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        when(riotMatchLookupService.getMatch(matchId))
                .thenReturn(matchDetail(matchId, START_AT.minusSeconds(3600), 420, true, 99));

        service.syncParticipant(challenge, participant, NOW);

        verify(participantMatchRepository, never()).save(any());
    }

    @Test
    void syncParticipant_skipsMatch_atOrAfterChallengeEnd() {
        String matchId = "EUW1_333";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        when(riotMatchLookupService.getMatch(matchId))
                .thenReturn(matchDetail(matchId, END_AT, 420, true, 99));

        service.syncParticipant(challenge, participant, NOW);

        verify(participantMatchRepository, never()).save(any());
    }

    @Test
    void syncParticipant_skipsMatch_offRankedSoloQueue() {
        String matchId = "EUW1_444";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        // Flex queue (440), not ranked solo/duo (420) — must never count toward a challenge.
        when(riotMatchLookupService.getMatch(matchId))
                .thenReturn(matchDetail(matchId, START_AT.plusSeconds(3600), 440, true, 99));

        service.syncParticipant(challenge, participant, NOW);

        verify(participantMatchRepository, never()).save(any());
    }

    @Test
    void syncParticipant_dedupsAlreadyLinkedMatch_doesNotReimport() {
        String matchId = "EUW1_555";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        when(participantMatchRepository.existsByParticipantIdAndRiotMatchId(participant.getId(), matchId))
                .thenReturn(true);

        service.syncParticipant(challenge, participant, NOW);

        verify(riotMatchLookupService, never()).getMatch(anyString());
        verify(championBackfillService, times(1)).backfillMatchIfMissing(participant, matchId);
        verify(participantMatchRepository, never()).save(any());
    }

    @Test
    void syncParticipant_stopsImportingAtPerRefreshCap() {
        when(participantMatchRepository.countByParticipantId(participant.getId())).thenReturn(1L);

        List<String> matchIds = java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> "EUW1_" + (600 + i))
                .toList();
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(matchIds);
        when(riotMatchLookupService.getMatch(anyString()))
                .thenAnswer(invocation -> matchDetail(invocation.getArgument(0), START_AT.plusSeconds(3600), 420, true, 99));

        service.syncParticipant(challenge, participant, NOW);

        verify(participantMatchRepository, times(ChallengeParticipantSyncService.MAX_NEW_MATCHES_PER_REFRESH)).save(any());
    }

    private static RiotMatchDetailDto matchDetail(String matchId, Instant gameStart, int queueId, boolean win, int championId) {
        return new RiotMatchDetailDto(
                new RiotMatchDetailDto.Metadata(matchId),
                new RiotMatchDetailDto.Info(
                        gameStart.toEpochMilli(),
                        queueId,
                        List.of(new RiotMatchDetailDto.Participant(PUUID, win, 4586, championId, "Champ"))
                )
        );
    }
}
