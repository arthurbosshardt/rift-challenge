package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.synchronization.RankSnapshot;
import com.riftchallenge.synchronization.RankSnapshotRepository;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChallengeProgressServiceTest {

    @Mock
    private RankSnapshotRepository rankSnapshotRepository;

    @Mock
    private ChallengeParticipantMatchRepository participantMatchRepository;

    @Mock
    private MatchHistoryService matchHistoryService;

    @InjectMocks
    private ChallengeProgressService challengeProgressService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(matchHistoryService.buildForParticipant(any(), any())).thenReturn(List.of());
    }

    @Test
    void buildForParticipant_whenMatchesMissing_usesLeagueSnapshotDelta() {
        UUID challengeId = UUID.randomUUID();
        ChallengeParticipant participant = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-1", "Catherine", "FEUR")
        );

        RankSnapshot baseline = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.parse("2026-08-01T00:00:00Z"),
                RankSnapshot.SnapshotType.BASELINE,
                "RANKED_SOLO_5x5",
                "GOLD",
                "IV",
                20,
                10,
                5
        );
        RankSnapshot refresh = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.parse("2026-08-17T00:00:00Z"),
                RankSnapshot.SnapshotType.REFRESH,
                "RANKED_SOLO_5x5",
                "GOLD",
                "III",
                42,
                15,
                12
        );

        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.BASELINE
        )).thenReturn(Optional.of(baseline));
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.REFRESH
        )).thenReturn(Optional.of(refresh));
        when(participantMatchRepository.countWinsInChallengeWindow(participant.getId(), challengeId)).thenReturn(0L);
        when(participantMatchRepository.countLossesInChallengeWindow(participant.getId(), challengeId)).thenReturn(0L);

        ParticipantProgressResponse progress = challengeProgressService.buildForParticipant(participant);

        assertThat(progress.wins()).isEqualTo(5);
        assertThat(progress.losses()).isEqualTo(7);
        assertThat(progress.hasRankData()).isTrue();
        assertThat(progress.lpGained()).isGreaterThan(0);
    }

    @Test
    void buildForParticipant_whenSeasonReset_usesChallengeWindowStatsOnRefreshSnapshot() {
        UUID challengeId = UUID.randomUUID();
        ChallengeParticipant participant = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-1", "Catherine", "FEUR")
        );

        RankSnapshot baseline = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.parse("2025-06-01T00:00:00Z"),
                RankSnapshot.SnapshotType.BASELINE,
                "RANKED_SOLO_5x5",
                "GOLD",
                "IV",
                20,
                0,
                0
        );
        RankSnapshot refresh = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.parse("2025-08-01T00:00:00Z"),
                RankSnapshot.SnapshotType.REFRESH,
                "RANKED_SOLO_5x5",
                "GOLD",
                "III",
                42,
                5,
                7
        );

        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.BASELINE
        )).thenReturn(Optional.of(baseline));
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.REFRESH
        )).thenReturn(Optional.of(refresh));
        when(participantMatchRepository.countWinsInChallengeWindow(participant.getId(), challengeId)).thenReturn(0L);
        when(participantMatchRepository.countLossesInChallengeWindow(participant.getId(), challengeId)).thenReturn(0L);

        ParticipantProgressResponse progress = challengeProgressService.buildForParticipant(participant);

        assertThat(progress.wins()).isEqualTo(5);
        assertThat(progress.losses()).isEqualTo(7);
    }

    @Test
    void buildForParticipant_whenSyncedMatchesExist_usesMatchCountsEvenWithZeroWins() {
        UUID challengeId = UUID.randomUUID();
        ChallengeParticipant participant = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-1", "Player", "EUW")
        );

        RankSnapshot baseline = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.now(),
                RankSnapshot.SnapshotType.BASELINE,
                "RANKED_SOLO_5x5",
                "SILVER",
                "I",
                0,
                0,
                0
        );
        RankSnapshot refresh = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.now(),
                RankSnapshot.SnapshotType.REFRESH,
                "RANKED_SOLO_5x5",
                "SILVER",
                "IV",
                12,
                0,
                7
        );

        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.BASELINE
        )).thenReturn(Optional.of(baseline));
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.REFRESH
        )).thenReturn(Optional.of(refresh));
        when(participantMatchRepository.countWinsInChallengeWindow(participant.getId(), challengeId)).thenReturn(0L);
        when(participantMatchRepository.countLossesInChallengeWindow(participant.getId(), challengeId)).thenReturn(7L);

        ParticipantProgressResponse progress = challengeProgressService.buildForParticipant(participant);

        assertThat(progress.wins()).isZero();
        assertThat(progress.losses()).isEqualTo(7);
    }

    @Test
    void buildForParticipant_whenOnlyBaselineSnapshotWithStats_usesStoredStats() {
        UUID challengeId = UUID.randomUUID();
        ChallengeParticipant participant = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-1", "Catherine", "FEUR")
        );

        RankSnapshot baseline = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.parse("2025-08-10T10:00:00Z"),
                RankSnapshot.SnapshotType.BASELINE,
                "RANKED_SOLO_5x5",
                "GOLD",
                "III",
                60,
                5,
                7
        );

        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.BASELINE
        )).thenReturn(Optional.of(baseline));
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.REFRESH
        )).thenReturn(Optional.empty());
        when(participantMatchRepository.countWinsInChallengeWindow(participant.getId(), challengeId)).thenReturn(0L);
        when(participantMatchRepository.countLossesInChallengeWindow(participant.getId(), challengeId)).thenReturn(0L);

        ParticipantProgressResponse progress = challengeProgressService.buildForParticipant(participant);

        assertThat(progress.wins()).isEqualTo(5);
        assertThat(progress.losses()).isEqualTo(7);
    }

    @Test
    void buildForParticipant_recomputesEstimatedRankFromSyncedMatches() {
        UUID challengeId = UUID.randomUUID();
        ChallengeParticipant participant = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-nikos", "Nikos Alachiasse", "777")
        );

        RankSnapshot baseline = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.parse("2025-02-17T01:00:00Z"),
                RankSnapshot.SnapshotType.BASELINE,
                "RANKED_SOLO_5x5",
                "IRON",
                "IV",
                0,
                0,
                0,
                true
        );
        RankSnapshot refresh = RankSnapshot.create(
                participant.getId(),
                java.time.Instant.parse("2025-05-03T00:00:00Z"),
                RankSnapshot.SnapshotType.REFRESH,
                "RANKED_SOLO_5x5",
                "BRONZE",
                "III",
                68,
                55,
                34,
                true
        );

        List<ParticipantMatchOutcomeInWindow> outcomes = new java.util.ArrayList<>();
        for (int i = 0; i < 55; i++) {
            outcomes.add(outcome(true));
        }
        for (int i = 0; i < 34; i++) {
            outcomes.add(outcome(false));
        }

        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.BASELINE
        )).thenReturn(Optional.of(baseline));
        when(rankSnapshotRepository.findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                participant.getId(),
                RankSnapshot.SnapshotType.REFRESH
        )).thenReturn(Optional.of(refresh));
        when(participantMatchRepository.countWinsInChallengeWindow(participant.getId(), challengeId)).thenReturn(55L);
        when(participantMatchRepository.countLossesInChallengeWindow(participant.getId(), challengeId)).thenReturn(34L);
        when(participantMatchRepository.findOutcomesInChallengeWindow(participant.getId(), challengeId))
                .thenReturn(outcomes);

        ParticipantProgressResponse progress = challengeProgressService.buildForParticipant(participant);

        assertThat(progress.currentTier()).isIn("EMERALD", "DIAMOND");
        assertThat(progress.rankEstimated()).isTrue();
        assertThat(progress.lpGained()).isGreaterThan(200);
    }

    private static ParticipantMatchOutcomeInWindow outcome(boolean win) {
        ParticipantMatchOutcomeInWindow outcome = mock(ParticipantMatchOutcomeInWindow.class);
        when(outcome.isWin()).thenReturn(win);
        return outcome;
    }
}
