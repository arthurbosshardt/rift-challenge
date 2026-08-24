package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.account.RiotAccountService;
import com.riftchallenge.challenge.dto.AddParticipantRequest;
import com.riftchallenge.challenge.dto.ParticipantResponse;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.synchronization.RankSnapshotRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChallengeParticipantServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private RiotAccountClient riotAccountClient;

    @Mock
    private RankSnapshotRepository rankSnapshotRepository;

    @Mock
    private RiotLeagueClient riotLeagueClient;

    @Mock
    private ParticipantProfileService participantProfileService;

    @Mock
    private RiotAccountService riotAccountService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private ChallengeParticipantService participantService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        participantService = new ChallengeParticipantService(
                challengeRepository,
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
    void addParticipant_whenNotOwner_throwsForbidden() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> participantService.addParticipant(
                challengeId,
                otherOwnerId,
                new AddParticipantRequest("Tanor#7154")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not the challenge owner");

        verify(riotAccountClient, never()).getAccountByRiotId(anyString(), anyString());
    }

    @Test
    void addParticipant_whenLimitReached_throwsBadRequest() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challengeId)).thenReturn(16L);

        assertThatThrownBy(() -> participantService.addParticipant(
                challengeId,
                ownerId,
                new AddParticipantRequest("Tanor#7154")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Participant limit reached");

        verify(riotAccountClient, never()).getAccountByRiotId(anyString(), anyString());
    }

    @Test
    void addParticipant_whenDuplicate_throwsConflict() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challengeId)).thenReturn(1L);
        when(riotAccountClient.getAccountByRiotId("Tanor", "7154")).thenReturn(account);
        when(participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, "puuid-1")).thenReturn(true);

        assertThatThrownBy(() -> participantService.addParticipant(
                challengeId,
                ownerId,
                new AddParticipantRequest("Tanor#7154")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Participant already added");
    }

    @Test
    void addParticipant_whenValid_persistsParticipant() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challengeId)).thenReturn(0L);
        when(riotAccountClient.getAccountByRiotId(eq("Tanor"), eq("7154"))).thenReturn(account);
        when(participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, "puuid-1")).thenReturn(false);
        when(participantRepository.save(org.mockito.ArgumentMatchers.any(ChallengeParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ParticipantResponse response = participantService.addParticipant(
                challengeId,
                ownerId,
                new AddParticipantRequest("Tanor#7154")
        );

        assertThat(response.riotId()).isEqualTo("Tanor#7154");
        assertThat(response.gameName()).isEqualTo("Tanor");
        assertThat(response.tagLine()).isEqualTo("7154");
        verify(participantRepository).save(org.mockito.ArgumentMatchers.any(ChallengeParticipant.class));
    }

    @Test
    void addParticipant_whenBaselineLookupFails_stillPersistsParticipant() {
        UUID challengeId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challengeId)).thenReturn(0L);
        when(riotAccountClient.getAccountByRiotId(eq("Tanor"), eq("7154"))).thenReturn(account);
        when(participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, "puuid-1")).thenReturn(false);
        when(participantRepository.save(org.mockito.ArgumentMatchers.any(ChallengeParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(riotLeagueClient.findRankedSoloEntry("puuid-1", com.riftchallenge.riot.ChallengeRegion.EUW))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Riot API request failed"));

        ParticipantResponse response = participantService.addParticipant(
                challengeId,
                ownerId,
                new AddParticipantRequest("Tanor#7154")
        );

        assertThat(response.riotId()).isEqualTo("Tanor#7154");
        verify(participantRepository).save(org.mockito.ArgumentMatchers.any(ChallengeParticipant.class));
    }

    @Test
    void removeParticipant_whenNotOwner_throwsForbidden() {
        UUID challengeId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> participantService.removeParticipant(challengeId, participantId, otherOwnerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not the challenge owner");

        verify(participantRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeParticipant_whenValid_deletesParticipant() {
        UUID challengeId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        Challenge challenge = Challenge.create(ownerId, "Test", ChallengeType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"));
        ChallengeParticipant participant = ChallengeParticipant.create(challengeId, account);

        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(participantRepository.findByIdAndChallengeId(participantId, challengeId)).thenReturn(Optional.of(participant));

        participantService.removeParticipant(challengeId, participantId, ownerId);

        verify(participantRepository).delete(participant);
    }
}
