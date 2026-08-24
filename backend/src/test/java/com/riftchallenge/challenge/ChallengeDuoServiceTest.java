package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.riftchallenge.account.RiotAccountService;
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
    private RiotAccountService riotAccountService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);
    private ChallengeDuoService duoService;

    @BeforeEach
    void setUp() {
        duoService = new ChallengeDuoService(
                challengeRepository,
                challengeDuoRepository,
                participantRepository,
                rankSnapshotRepository,
                riotAccountClient,
                riotLeagueClient,
                participantProfileService,
                riotAccountService,
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
}
