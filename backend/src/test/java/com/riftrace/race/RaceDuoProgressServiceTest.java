package com.riftrace.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.riftrace.race.dto.ParticipantProgressResponse;
import com.riftrace.riot.dto.RiotAccountDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RaceDuoProgressServiceTest {

    @Mock
    private RaceDuoRepository raceDuoRepository;

    @Mock
    private RaceParticipantRepository participantRepository;

    @Mock
    private RaceProgressService progressService;

    @Mock
    private DuoEligibilityService duoEligibilityService;

    @InjectMocks
    private RaceDuoProgressService raceDuoProgressService;

    @Test
    void buildProgress_whenEligibleWithoutSyncedTogetherMatches_usesIndividualStatsFallback() {
        UUID raceId = UUID.randomUUID();
        RaceDuo duo = RaceDuo.create(raceId);
        RaceParticipant player1 = RaceParticipant.create(
                raceId,
                new RiotAccountDto("puuid-1", "Catherine", "FEUR"),
                duo.getId()
        );
        RaceParticipant player2 = RaceParticipant.create(
                raceId,
                new RiotAccountDto("puuid-2", "Peace", "FEUR"),
                duo.getId()
        );

        ParticipantProgressResponse progress1 = ParticipantProgressResponse.withRankData(
                player1,
                0,
                "GOLD",
                "III",
                42,
                18,
                1_500,
                5,
                7
        );
        ParticipantProgressResponse progress2 = ParticipantProgressResponse.withRankData(
                player2,
                0,
                "GOLD",
                "III",
                42,
                22,
                1_500,
                5,
                7
        );

        when(raceDuoRepository.findByRaceIdOrderByCreatedAtAsc(raceId)).thenReturn(List.of(duo));
        when(participantRepository.findByDuoIdOrderByCreatedAtAsc(duo.getId()))
                .thenReturn(List.of(player1, player2));
        when(progressService.buildForParticipant(player1)).thenReturn(progress1);
        when(progressService.buildForParticipant(player2)).thenReturn(progress2);
        when(duoEligibilityService.evaluate(player1, player2))
                .thenReturn(new DuoEligibilityService.DuoEligibility(true, null, java.util.Set.of()));
        when(duoEligibilityService.statsForTogetherMatches(player1, java.util.Set.of()))
                .thenReturn(new DuoEligibilityService.DuoMatchStats(0, 0));

        var duos = raceDuoProgressService.buildProgress(raceId);

        assertThat(duos).hasSize(1);
        assertThat(duos.getFirst().wins()).isEqualTo(5);
        assertThat(duos.getFirst().losses()).isEqualTo(7);
        assertThat(duos.getFirst().combinedLpGained()).isEqualTo(40);
        assertThat(duos.getFirst().eligible()).isTrue();
    }

    @Test
    void buildProgress_whenIneligibleWithoutTogetherMatches_keepsZeroStats() {
        UUID raceId = UUID.randomUUID();
        RaceDuo duo = RaceDuo.create(raceId);
        RaceParticipant player1 = RaceParticipant.create(
                raceId,
                new RiotAccountDto("puuid-1", "Catherine", "FEUR"),
                duo.getId()
        );
        RaceParticipant player2 = RaceParticipant.create(
                raceId,
                new RiotAccountDto("puuid-2", "Peace", "FEUR"),
                duo.getId()
        );

        ParticipantProgressResponse progress1 = ParticipantProgressResponse.withRankData(
                player1, 0, "GOLD", "III", 42, 18, 1_500, 5, 7
        );
        ParticipantProgressResponse progress2 = ParticipantProgressResponse.withRankData(
                player2, 0, "GOLD", "III", 42, 22, 1_500, 3, 4
        );

        when(raceDuoRepository.findByRaceIdOrderByCreatedAtAsc(raceId)).thenReturn(List.of(duo));
        when(participantRepository.findByDuoIdOrderByCreatedAtAsc(duo.getId()))
                .thenReturn(List.of(player1, player2));
        when(progressService.buildForParticipant(any())).thenReturn(progress1, progress2);
        when(duoEligibilityService.evaluate(player1, player2))
                .thenReturn(new DuoEligibilityService.DuoEligibility(
                        false,
                        "SOLOQ_WITHOUT_PARTNER|Catherine#FEUR",
                        java.util.Set.of("match-1")
                ));
        when(duoEligibilityService.statsForTogetherMatches(player1, java.util.Set.of("match-1")))
                .thenReturn(new DuoEligibilityService.DuoMatchStats(0, 0));

        var duos = raceDuoProgressService.buildProgress(raceId);

        assertThat(duos.getFirst().wins()).isZero();
        assertThat(duos.getFirst().losses()).isZero();
        assertThat(duos.getFirst().eligible()).isFalse();
    }
}
