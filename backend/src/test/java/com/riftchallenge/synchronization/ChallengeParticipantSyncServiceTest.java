package com.riftchallenge.synchronization;

import static org.assertj.core.api.Assertions.assertThatCode;
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
import com.riftchallenge.leaderboard.AccountMatchRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    private AccountMatchRepository participantMatchRepository;

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

        // Not-finished challenge: keeps the historical/estimated-rank branch a no-op so most tests
        // stay focused on match-window sync behaviour. lenient(): the finished-challenge tests
        // below never reach this call at all (different branch), which strict stubbing would
        // otherwise flag as unnecessary.
        org.mockito.Mockito.lenient().when(riotLeagueClient.findRankedSoloEntry(any(), any())).thenReturn(Optional.empty());
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
        when(participantMatchRepository.existsByRiotPuuidAndRiotMatchId(PUUID, matchId))
                .thenReturn(true);

        service.syncParticipant(challenge, participant, NOW);

        verify(riotMatchLookupService, never()).getMatch(anyString());
        verify(championBackfillService, times(1)).backfillMatchIfMissing(participant, matchId);
        verify(participantMatchRepository, never()).save(any());
    }

    @Test
    void syncParticipant_stopsImportingAtPerRefreshCap() {
        when(participantMatchRepository.countInChallengeWindow(participant.getId(), challenge.getId())).thenReturn(1L);

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

    @Test
    void syncChallengeWindowMatches_stopsOnRateLimit_keepingMatchesImportedSoFar() {
        String importedMatchId = "EUW1_700";
        String rateLimitedMatchId = "EUW1_701";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(importedMatchId, rateLimitedMatchId));
        when(riotMatchLookupService.getMatch(importedMatchId))
                .thenReturn(matchDetail(importedMatchId, START_AT.plusSeconds(3600), 420, true, 99));
        when(riotMatchLookupService.getMatch(rateLimitedMatchId))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Riot API rate limit reached"));

        // A 429 partway through the loop must not fail the whole sync — it should stop importing
        // and keep whatever was already saved, letting the next refresh pick up where it left off.
        assertThatCode(() -> service.syncParticipant(challenge, participant, NOW)).doesNotThrowAnyException();

        verify(participantMatchRepository, times(1)).save(any());
    }

    @Test
    void importMatchDetail_toleratesConcurrentDuplicateAccountMatch() {
        String matchId = "EUW1_800";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        when(riotMatchLookupService.getMatch(matchId))
                .thenReturn(matchDetail(matchId, START_AT.plusSeconds(3600), 420, true, 99));
        // Simulates another concurrent sync (e.g. a linked leaderboard sync) having already
        // inserted the same (riot_puuid, riot_match_id) row between the existence check and save.
        when(participantMatchRepository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatCode(() -> service.syncParticipant(challenge, participant, NOW)).doesNotThrowAnyException();
    }

    @Test
    void persistMatchIfNeeded_toleratesConcurrentDuplicateRiotMatch() {
        String matchId = "EUW1_900";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(matchId));
        when(riotMatchLookupService.getMatch(matchId))
                .thenReturn(matchDetail(matchId, START_AT.plusSeconds(3600), 420, true, 99));
        when(riotMatchRepository.existsByRiotMatchId(matchId)).thenReturn(false);
        // Simulates another concurrent participant's sync having already inserted this shared
        // riot_match row (matches aren't scoped per-participant) between the check and the save.
        when(riotMatchRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatCode(() -> service.syncParticipant(challenge, participant, NOW)).doesNotThrowAnyException();

        // The shared riot_match row lost the race, but this participant's own account_match link
        // is independent and must still be persisted.
        verify(participantMatchRepository, times(1)).save(any());
    }

    @Test
    void syncParticipant_finishedChallengeFullySynced_skipsRiotSyncEntirely() {
        Instant afterEnd = END_AT.plusSeconds(3600);
        RankSnapshot baseline = RankSnapshot.create(
                participant.getId(), START_AT, RankSnapshot.SnapshotType.BASELINE,
                "RANKED_SOLO_5x5", "GOLD", "IV", 20, 0, 0
        );
        RankSnapshot refresh = RankSnapshot.create(
                participant.getId(), END_AT, RankSnapshot.SnapshotType.REFRESH,
                "RANKED_SOLO_5x5", "GOLD", "II", 40, 5, 3
        );
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(), RankSnapshot.SnapshotType.BASELINE
        )).thenReturn(Optional.of(baseline));
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(), RankSnapshot.SnapshotType.REFRESH
        )).thenReturn(Optional.of(refresh));
        when(participantMatchRepository.countInChallengeWindow(participant.getId(), challenge.getId())).thenReturn(5L);
        when(participantMatchRepository.countMissingChampionIdByRiotPuuid(PUUID)).thenReturn(0L);
        // hasPendingMatchImports() re-lists match ids and finds them all already linked.
        String alreadyLinkedMatchId = "EUW1_950";
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of(alreadyLinkedMatchId));
        when(participantMatchRepository.existsByRiotPuuidAndRiotMatchId(PUUID, alreadyLinkedMatchId)).thenReturn(true);

        service.syncParticipant(challenge, participant, afterEnd);

        // The whole match-window sync (and its champion backfill) must be skipped, not just a no-op pass.
        verify(championBackfillService, never()).backfillForParticipant(any());
        verify(riotMatchLookupService, never()).getMatch(anyString());
    }

    @Test
    void syncParticipant_finishedChallengeWithMissingChampionIds_doesNotSkip() {
        Instant afterEnd = END_AT.plusSeconds(3600);
        RankSnapshot baseline = RankSnapshot.create(
                participant.getId(), START_AT, RankSnapshot.SnapshotType.BASELINE,
                "RANKED_SOLO_5x5", "GOLD", "IV", 20, 0, 0
        );
        RankSnapshot refresh = RankSnapshot.create(
                participant.getId(), END_AT, RankSnapshot.SnapshotType.REFRESH,
                "RANKED_SOLO_5x5", "GOLD", "II", 40, 5, 3
        );
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(), RankSnapshot.SnapshotType.BASELINE
        )).thenReturn(Optional.of(baseline));
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(), RankSnapshot.SnapshotType.REFRESH
        )).thenReturn(Optional.of(refresh));
        when(participantMatchRepository.countInChallengeWindow(participant.getId(), challenge.getId())).thenReturn(5L);
        // Still some rows missing champion_id — must not take the "fully synced" skip path.
        when(participantMatchRepository.countMissingChampionIdByRiotPuuid(PUUID)).thenReturn(2L);
        when(riotMatchClient.getAllRankedSoloMatchIdsInWindow(eq(PUUID), any(Long.class), any(), any(Integer.class), any()))
                .thenReturn(List.of());

        service.syncParticipant(challenge, participant, afterEnd);

        verify(championBackfillService, times(1)).backfillForParticipant(participant.getId());
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
