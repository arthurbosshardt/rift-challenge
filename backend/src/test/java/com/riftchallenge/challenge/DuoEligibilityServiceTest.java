package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.leaderboard.AccountMatchRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DuoEligibilityServiceTest {

    @Mock
    private AccountMatchRepository participantMatchRepository;

    @InjectMocks
    private DuoEligibilityService duoEligibilityService;

    @Test
    void evaluate_whenPlayersOnlyQueuedTogether_isEligible() {
        UUID challengeId = UUID.randomUUID();
        ChallengeParticipant player1 = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-1", "Tanor", "7154")
        );
        ChallengeParticipant player2 = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-2", "Kaori", "EUW33")
        );

        when(participantMatchRepository.findMatchIdsInChallengeWindow(player1.getId(), challengeId))
                .thenReturn(List.of("match-1", "match-2"));
        when(participantMatchRepository.findMatchIdsInChallengeWindow(player2.getId(), challengeId))
                .thenReturn(List.of("match-1", "match-2"));

        DuoEligibilityService.DuoEligibility eligibility = duoEligibilityService.evaluate(challengeId, player1, player2);

        assertThat(eligibility.eligible()).isTrue();
        assertThat(eligibility.reason()).isNull();
        assertThat(eligibility.togetherMatchIds()).containsExactlyInAnyOrder("match-1", "match-2");
    }

    @Test
    void evaluate_whenOnePlayerQueuedAlone_isIneligible() {
        UUID challengeId = UUID.randomUUID();
        ChallengeParticipant player1 = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-1", "Tanor", "7154")
        );
        ChallengeParticipant player2 = ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-2", "Kaori", "EUW33")
        );

        when(participantMatchRepository.findMatchIdsInChallengeWindow(player1.getId(), challengeId))
                .thenReturn(List.of("match-1", "match-solo"));
        when(participantMatchRepository.findMatchIdsInChallengeWindow(player2.getId(), challengeId))
                .thenReturn(List.of("match-1"));

        DuoEligibilityService.DuoEligibility eligibility = duoEligibilityService.evaluate(challengeId, player1, player2);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).isEqualTo("SOLOQ_WITHOUT_PARTNER|Tanor#7154");
    }
}
