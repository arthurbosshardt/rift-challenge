package com.riftrace.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.riftrace.race.dto.ParticipantProgressResponse;
import com.riftrace.riot.dto.RiotAccountDto;
import com.riftrace.synchronization.RankSnapshot;
import com.riftrace.synchronization.RankSnapshotRepository;
import com.riftrace.synchronization.RaceParticipantMatchRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RaceProgressServiceTest {

    @Mock
    private RankSnapshotRepository rankSnapshotRepository;

    @Mock
    private RaceParticipantMatchRepository participantMatchRepository;

    @InjectMocks
    private RaceProgressService raceProgressService;

    @Test
    void buildForParticipant_whenMatchesMissing_usesLeagueSnapshotDelta() {
        UUID raceId = UUID.randomUUID();
        RaceParticipant participant = RaceParticipant.create(
                raceId,
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
        when(participantMatchRepository.countWinsByParticipantId(participant.getId())).thenReturn(0L);
        when(participantMatchRepository.countLossesByParticipantId(participant.getId())).thenReturn(0L);

        ParticipantProgressResponse progress = raceProgressService.buildForParticipant(participant);

        assertThat(progress.wins()).isEqualTo(5);
        assertThat(progress.losses()).isEqualTo(7);
        assertThat(progress.hasRankData()).isTrue();
        assertThat(progress.lpGained()).isGreaterThan(0);
    }

    @Test
    void buildForParticipant_whenSeasonReset_usesRaceWindowStatsOnRefreshSnapshot() {
        UUID raceId = UUID.randomUUID();
        RaceParticipant participant = RaceParticipant.create(
                raceId,
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
        when(participantMatchRepository.countWinsByParticipantId(participant.getId())).thenReturn(0L);
        when(participantMatchRepository.countLossesByParticipantId(participant.getId())).thenReturn(0L);

        ParticipantProgressResponse progress = raceProgressService.buildForParticipant(participant);

        assertThat(progress.wins()).isEqualTo(5);
        assertThat(progress.losses()).isEqualTo(7);
    }

    @Test
    void buildForParticipant_whenSyncedMatchesExist_usesMatchCountsEvenWithZeroWins() {
        UUID raceId = UUID.randomUUID();
        RaceParticipant participant = RaceParticipant.create(
                raceId,
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
        when(participantMatchRepository.countWinsByParticipantId(participant.getId())).thenReturn(0L);
        when(participantMatchRepository.countLossesByParticipantId(participant.getId())).thenReturn(7L);

        ParticipantProgressResponse progress = raceProgressService.buildForParticipant(participant);

        assertThat(progress.wins()).isZero();
        assertThat(progress.losses()).isEqualTo(7);
    }

    @Test
    void buildForParticipant_whenOnlyBaselineSnapshotWithStats_usesStoredStats() {
        UUID raceId = UUID.randomUUID();
        RaceParticipant participant = RaceParticipant.create(
                raceId,
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
        when(participantMatchRepository.countWinsByParticipantId(participant.getId())).thenReturn(0L);
        when(participantMatchRepository.countLossesByParticipantId(participant.getId())).thenReturn(0L);

        ParticipantProgressResponse progress = raceProgressService.buildForParticipant(participant);

        assertThat(progress.wins()).isEqualTo(5);
        assertThat(progress.losses()).isEqualTo(7);
    }
}
