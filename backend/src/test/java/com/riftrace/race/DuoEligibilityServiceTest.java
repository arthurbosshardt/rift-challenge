package com.riftrace.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.riftrace.riot.dto.RiotAccountDto;
import com.riftrace.synchronization.RaceParticipantMatchRepository;
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
    private RaceParticipantMatchRepository participantMatchRepository;

    @InjectMocks
    private DuoEligibilityService duoEligibilityService;

    @Test
    void evaluate_whenPlayersOnlyQueuedTogether_isEligible() {
        UUID raceId = UUID.randomUUID();
        RaceParticipant player1 = RaceParticipant.create(
                raceId,
                new RiotAccountDto("puuid-1", "Tanor", "7154")
        );
        RaceParticipant player2 = RaceParticipant.create(
                raceId,
                new RiotAccountDto("puuid-2", "Kaori", "EUW33")
        );

        when(participantMatchRepository.findMatchIdsByParticipantId(player1.getId()))
                .thenReturn(List.of("match-1", "match-2"));
        when(participantMatchRepository.findMatchIdsByParticipantId(player2.getId()))
                .thenReturn(List.of("match-1", "match-2"));

        DuoEligibilityService.DuoEligibility eligibility = duoEligibilityService.evaluate(player1, player2);

        assertThat(eligibility.eligible()).isTrue();
        assertThat(eligibility.reason()).isNull();
        assertThat(eligibility.togetherMatchIds()).containsExactlyInAnyOrder("match-1", "match-2");
    }

    @Test
    void evaluate_whenOnePlayerQueuedAlone_isIneligible() {
        UUID raceId = UUID.randomUUID();
        RaceParticipant player1 = RaceParticipant.create(
                raceId,
                new RiotAccountDto("puuid-1", "Tanor", "7154")
        );
        RaceParticipant player2 = RaceParticipant.create(
                raceId,
                new RiotAccountDto("puuid-2", "Kaori", "EUW33")
        );

        when(participantMatchRepository.findMatchIdsByParticipantId(player1.getId()))
                .thenReturn(List.of("match-1", "match-solo"));
        when(participantMatchRepository.findMatchIdsByParticipantId(player2.getId()))
                .thenReturn(List.of("match-1"));

        DuoEligibilityService.DuoEligibility eligibility = duoEligibilityService.evaluate(player1, player2);

        assertThat(eligibility.eligible()).isFalse();
        assertThat(eligibility.reason()).isEqualTo("SOLOQ_WITHOUT_PARTNER|Tanor#7154");
    }
}
