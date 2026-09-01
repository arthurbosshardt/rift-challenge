package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.dto.AddDuoRequest;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.synchronization.RankSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChallengeDuoServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;
    @Mock
    private ChallengeDuoRepository challengeDuoRepository;
    @Mock
    private ChallengeParticipantRepository participantRepository;
    @Mock
    private RankSnapshotRepository rankSnapshotRepository;
    @Mock
    private RiotAccountClient riotAccountClient;
    @Mock
    private RiotLeagueClient riotLeagueClient;
    @Mock
    private ParticipantProfileService participantProfileService;
    @Mock
    private ChallengeDuoWriter duoWriter;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
    private ChallengeDuoService duoService;

    @BeforeEach
    void setUp() {
        BaselineSnapshotService baselineSnapshotService =
                new BaselineSnapshotService(riotLeagueClient, rankSnapshotRepository, clock);
        duoService = new ChallengeDuoService(
                challengeRepository,
                challengeDuoRepository,
                participantRepository,
                baselineSnapshotService,
                riotAccountClient,
                participantProfileService,
                duoWriter,
                clock
        );
    }

    @Test
    void addDuo_whenPlayer1Missing_throwsNotFoundForPlayer1() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Duo", ChallengeType.DUOQ, Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeDuoRepository.countByChallengeId(challengeId)).thenReturn(0L);
        when(riotAccountClient.getAccountByRiotId("Missing", "EUW"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Riot account not found"));

        assertThatThrownBy(() -> duoService.addDuo(
                challengeId,
                ownerId,
                new AddDuoRequest("Missing#EUW", "Tanor#7154")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Riot account not found for player 1");
    }

    @Test
    void addDuo_whenPlayer2Missing_throwsNotFoundForPlayer2() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Duo", ChallengeType.DUOQ, Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeDuoRepository.countByChallengeId(challengeId)).thenReturn(0L);
        when(riotAccountClient.getAccountByRiotId("Tanor", "7154"))
                .thenReturn(new RiotAccountDto("puuid-1", "Tanor", "7154"));
        when(riotAccountClient.getAccountByRiotId("Missing", "EUW"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Riot account not found"));

        assertThatThrownBy(() -> duoService.addDuo(
                challengeId,
                ownerId,
                new AddDuoRequest("Tanor#7154", "Missing#EUW")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Riot account not found for player 2");
    }

    @Test
    void addDuo_whenNotOwner_throwsForbidden() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Duo", ChallengeType.DUOQ, Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> duoService.addDuo(
                challengeId,
                callerId,
                new AddDuoRequest("Tanor#7154", "Other#EUW")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);
    }

    @Test
    void addDuo_whenLimitReached_throwsBadRequest() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Duo", ChallengeType.DUOQ, Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeDuoRepository.countByChallengeId(challengeId)).thenReturn(8L);

        assertThatThrownBy(() -> duoService.addDuo(
                challengeId,
                ownerId,
                new AddDuoRequest("Tanor#7154", "Other#EUW")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.BAD_REQUEST)
                .hasMessageContaining("Duo limit reached");
    }

    @Test
    void removeDuo_whenNotOwner_throwsForbidden() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID duoId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Duo", ChallengeType.DUOQ, Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> duoService.removeDuo(challengeId, duoId, callerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.FORBIDDEN);

        verify(challengeDuoRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeDuo_whenDuoBelongsToDifferentChallenge_throwsNotFound() {
        UUID challengeId = UUID.randomUUID();
        UUID otherChallengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Duo", ChallengeType.DUOQ, Instant.parse("2026-12-01T18:00:00Z"));
        ChallengeDuo duoFromOtherChallenge = ChallengeDuo.create(otherChallengeId);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeDuoRepository.findById(duoFromOtherChallenge.getId()))
                .thenReturn(Optional.of(duoFromOtherChallenge));

        assertThatThrownBy(() -> duoService.removeDuo(challengeId, duoFromOtherChallenge.getId(), ownerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.NOT_FOUND);

        verify(challengeDuoRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeDuo_whenOwnerAndDuoBelongsToChallenge_deletesDuo() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId, "Duo", ChallengeType.DUOQ, Instant.parse("2026-12-01T18:00:00Z"));
        ChallengeDuo duo = ChallengeDuo.create(challengeId);
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeDuoRepository.findById(duo.getId())).thenReturn(Optional.of(duo));

        duoService.removeDuo(challengeId, duo.getId(), ownerId);

        verify(challengeDuoRepository, times(1)).delete(duo);
    }
}
