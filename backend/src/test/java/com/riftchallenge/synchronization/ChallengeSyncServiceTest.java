package com.riftchallenge.synchronization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeRefresh;
import com.riftchallenge.challenge.ChallengeRefreshRecordService;
import com.riftchallenge.challenge.ChallengeRefreshRepository;
import com.riftchallenge.challenge.ChallengeRepository;
import com.riftchallenge.challenge.ChallengeType;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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

    @Mock
    private RiotMatchLookupService riotMatchLookupService;

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
                riotMatchLookupService,
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
                riotMatchLookupService,
                clock
        );

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, Instant.parse("2026-08-16T10:00:00Z"), false);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> challengeSyncService.refreshChallenge(challengeId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Challenge has not started yet");
    }

    @Test
    void refreshChallenge_withoutParticipants_skipsSyncAndCooldown() {
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
                riotMatchLookupService,
                clock
        );

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, Instant.parse("2026-08-16T09:00:00Z"), false);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())).thenReturn(List.of());

        challengeSyncService.refreshChallenge(challengeId);

        verify(participantSyncService, never()).syncParticipant(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(refreshRecordService, never()).recordRefresh(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(riotMatchLookupService, never()).beginRefreshScope();
    }

    @Test
    void refreshChallenge_rateLimitOnOneParticipant_continuesWithOthers() {
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
                riotMatchLookupService,
                clock
        );

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, Instant.parse("2026-08-16T09:00:00Z"), false);
        ChallengeParticipant first = ChallengeParticipant.create(
                challenge.getId(),
                new RiotAccountDto("puuid-1", "PlayerOne", "EUW")
        );
        ChallengeParticipant second = ChallengeParticipant.create(
                challenge.getId(),
                new RiotAccountDto("puuid-2", "PlayerTwo", "EUW")
        );

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())).thenReturn(List.of(first, second));
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Riot API rate limit reached"))
                .when(participantSyncService)
                .syncParticipant(challenge, first, now);

        assertThatThrownBy(() -> challengeSyncService.refreshChallenge(challengeId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("partial sync saved");

        verify(participantSyncService).syncParticipant(challenge, first, now);
        verify(participantSyncService).syncParticipant(challenge, second, now);
        verify(refreshRecordService).recordRefresh(challengeId, now);
        verify(riotMatchLookupService).beginRefreshScope();
        verify(riotMatchLookupService).endRefreshScope();
    }
}
