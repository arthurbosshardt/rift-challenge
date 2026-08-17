package com.riftchallenge.synchronization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.challenge.ChallengeRefresh;
import com.riftchallenge.challenge.ChallengeRefreshRecordService;
import com.riftchallenge.challenge.ChallengeRefreshRepository;
import com.riftchallenge.challenge.ChallengeRepository;
import com.riftchallenge.challenge.ChallengeType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChallengeSyncServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private ChallengeRefreshRepository challengeRefreshRepository;

    @Mock
    private ChallengeParticipantSyncService participantSyncService;

    @Mock
    private ChallengeRefreshRecordService refreshRecordService;

    @InjectMocks
    private ChallengeSyncService challengeSyncService;

    @Test
    void refreshChallenge_beforeCooldown_throwsTooManyRequests() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-16T10:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        challengeSyncService = new ChallengeSyncService(
                challengeRepository,
                participantRepository,
                challengeRefreshRepository,
                participantSyncService,
                refreshRecordService,
                clock
        );

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, Instant.parse("2026-08-16T09:00:00Z"), false);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeRefreshRepository.findByChallengeId(challengeId)).thenReturn(Optional.of(
                ChallengeRefresh.create(challengeId, Instant.parse("2026-08-16T09:59:30Z"))
        ));

        assertThatThrownBy(() -> challengeSyncService.refreshChallenge(challengeId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Refresh available at");

        verify(participantRepository, never()).findByChallengeIdOrderByCreatedAtAsc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshChallenge_beforeStart_throwsBadRequest() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-16T09:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        challengeSyncService = new ChallengeSyncService(
                challengeRepository,
                participantRepository,
                challengeRefreshRepository,
                participantSyncService,
                refreshRecordService,
                clock
        );

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, Instant.parse("2026-08-16T10:00:00Z"), false);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeSyncService.refreshChallenge(challengeId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Challenge has not started yet");
    }
}
