package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChallengeDuoProgressServiceTest {

    @Mock
    private ChallengeDuoRepository challengeDuoRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private ChallengeProgressService progressService;

    @Mock
    private DuoEligibilityService duoEligibilityService;

    @Mock
    private MatchHistoryService matchHistoryService;

    @InjectMocks
    private ChallengeDuoProgressService challengeDuoProgressService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(matchHistoryService.buildForDuo(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void buildProgress_whenEligibleWithoutSyncedTogetherMatches_usesIndividualStatsFallback() {
        Challenge challenge = Challenge.create(
                UUID.randomUUID(), "Test", ChallengeType.DUOQ, Instant.now(), true
        );
        UUID challengeId = challenge.getId();
        ChallengeDuo duo = ChallengeDuo.create(challengeId);
        ChallengeParticipant player1 = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-1", "Catherine", "FEUR"),
                duo.getId()
        );
        ChallengeParticipant player2 = ChallengeParticipant.create(
                challengeId,
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
                7,
                false
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
                7,
                false
        );

        when(challengeDuoRepository.findByChallengeIdOrderByCreatedAtAsc(challengeId)).thenReturn(List.of(duo));
        when(participantRepository.findByDuoIdOrderByCreatedAtAsc(duo.getId()))
                .thenReturn(List.of(player1, player2));
        when(progressService.buildForParticipant(player1, true, challenge)).thenReturn(progress1);
        when(progressService.buildForParticipant(player2, true, challenge)).thenReturn(progress2);
        when(duoEligibilityService.evaluate(challengeId, player1, player2))
                .thenReturn(new DuoEligibilityService.DuoEligibility(true, null, java.util.Set.of()));
        when(duoEligibilityService.statsForTogetherMatches(player1, java.util.Set.of(), null))
                .thenReturn(new DuoEligibilityService.DuoMatchStats(0, 0));

        var duos = challengeDuoProgressService.buildProgress(challenge);

        assertThat(duos).hasSize(1);
        assertThat(duos.getFirst().wins()).isEqualTo(5);
        assertThat(duos.getFirst().losses()).isEqualTo(7);
        assertThat(duos.getFirst().combinedLpGained()).isEqualTo(40);
        assertThat(duos.getFirst().eligible()).isTrue();
    }

    @Test
    void buildProgress_whenIneligibleWithoutTogetherMatches_keepsZeroStats() {
        Challenge challenge = Challenge.create(
                UUID.randomUUID(), "Test", ChallengeType.DUOQ, Instant.now(), true
        );
        UUID challengeId = challenge.getId();
        ChallengeDuo duo = ChallengeDuo.create(challengeId);
        ChallengeParticipant player1 = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-1", "Catherine", "FEUR"),
                duo.getId()
        );
        ChallengeParticipant player2 = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-2", "Peace", "FEUR"),
                duo.getId()
        );

        ParticipantProgressResponse progress1 = ParticipantProgressResponse.withRankData(
                player1, 0, "GOLD", "III", 42, 18, 1_500, 5, 7, false
        );
        ParticipantProgressResponse progress2 = ParticipantProgressResponse.withRankData(
                player2, 0, "GOLD", "III", 42, 22, 1_500, 3, 4, false
        );

        when(challengeDuoRepository.findByChallengeIdOrderByCreatedAtAsc(challengeId)).thenReturn(List.of(duo));
        when(participantRepository.findByDuoIdOrderByCreatedAtAsc(duo.getId()))
                .thenReturn(List.of(player1, player2));
        when(progressService.buildForParticipant(any(), anyBoolean(), any())).thenReturn(progress1, progress2);
        when(duoEligibilityService.evaluate(challengeId, player1, player2))
                .thenReturn(new DuoEligibilityService.DuoEligibility(
                        false,
                        "SOLOQ_WITHOUT_PARTNER|Catherine#FEUR",
                        java.util.Set.of("match-1")
                ));
        when(duoEligibilityService.statsForTogetherMatches(player1, java.util.Set.of("match-1"), null))
                .thenReturn(new DuoEligibilityService.DuoMatchStats(0, 0));

        var duos = challengeDuoProgressService.buildProgress(challenge);

        assertThat(duos.getFirst().wins()).isZero();
        assertThat(duos.getFirst().losses()).isZero();
        assertThat(duos.getFirst().eligible()).isFalse();
    }
}
