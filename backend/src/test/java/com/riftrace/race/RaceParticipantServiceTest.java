package com.riftrace.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftrace.race.dto.AddParticipantRequest;
import com.riftrace.race.dto.ParticipantResponse;
import com.riftrace.riot.RiotAccountClient;
import com.riftrace.riot.RiotLeagueClient;
import com.riftrace.riot.dto.RiotAccountDto;
import com.riftrace.synchronization.RankSnapshotRepository;
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
class RaceParticipantServiceTest {

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private RaceParticipantRepository participantRepository;

    @Mock
    private RiotAccountClient riotAccountClient;

    @Mock
    private RankSnapshotRepository rankSnapshotRepository;

    @Mock
    private RiotLeagueClient riotLeagueClient;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-16T10:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private RaceParticipantService participantService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        participantService = new RaceParticipantService(
                raceRepository,
                participantRepository,
                rankSnapshotRepository,
                riotAccountClient,
                riotLeagueClient,
                clock
        );
    }

    @Test
    void addParticipant_whenNotOwner_throwsForbidden() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();

        Race race = Race.create(ownerId, "Test", RaceType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"), false);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> participantService.addParticipant(
                raceId,
                otherOwnerId,
                new AddParticipantRequest("Tanor#7154")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not the race owner");

        verify(riotAccountClient, never()).getAccountByRiotId(anyString(), anyString());
    }

    @Test
    void addParticipant_whenLimitReached_throwsBadRequest() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        Race race = Race.create(ownerId, "Test", RaceType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"), false);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.countByRaceId(raceId)).thenReturn(16L);

        assertThatThrownBy(() -> participantService.addParticipant(
                raceId,
                ownerId,
                new AddParticipantRequest("Tanor#7154")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Participant limit reached");

        verify(riotAccountClient, never()).getAccountByRiotId(anyString(), anyString());
    }

    @Test
    void addParticipant_whenDuplicate_throwsConflict() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        Race race = Race.create(ownerId, "Test", RaceType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"), false);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.countByRaceId(raceId)).thenReturn(1L);
        when(riotAccountClient.getAccountByRiotId("Tanor", "7154")).thenReturn(account);
        when(participantRepository.existsByRaceIdAndRiotPuuid(raceId, "puuid-1")).thenReturn(true);

        assertThatThrownBy(() -> participantService.addParticipant(
                raceId,
                ownerId,
                new AddParticipantRequest("Tanor#7154")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Participant already added");
    }

    @Test
    void addParticipant_whenValid_persistsParticipant() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        Race race = Race.create(ownerId, "Test", RaceType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"), false);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.countByRaceId(raceId)).thenReturn(0L);
        when(riotAccountClient.getAccountByRiotId(eq("Tanor"), eq("7154"))).thenReturn(account);
        when(participantRepository.existsByRaceIdAndRiotPuuid(raceId, "puuid-1")).thenReturn(false);
        when(participantRepository.save(org.mockito.ArgumentMatchers.any(RaceParticipant.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ParticipantResponse response = participantService.addParticipant(
                raceId,
                ownerId,
                new AddParticipantRequest("Tanor#7154")
        );

        assertThat(response.riotId()).isEqualTo("Tanor#7154");
        assertThat(response.gameName()).isEqualTo("Tanor");
        assertThat(response.tagLine()).isEqualTo("7154");
        verify(participantRepository).save(org.mockito.ArgumentMatchers.any(RaceParticipant.class));
    }

    @Test
    void removeParticipant_whenNotOwner_throwsForbidden() {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID otherOwnerId = UUID.randomUUID();

        Race race = Race.create(ownerId, "Test", RaceType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"), false);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> participantService.removeParticipant(raceId, participantId, otherOwnerId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Not the race owner");

        verify(participantRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeParticipant_whenValid_deletesParticipant() {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        RiotAccountDto account = new RiotAccountDto("puuid-1", "Tanor", "7154");

        Race race = Race.create(ownerId, "Test", RaceType.SOLOQ, java.time.Instant.parse("2026-12-01T18:00:00Z"), false);
        RaceParticipant participant = RaceParticipant.create(raceId, account);

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.findByIdAndRaceId(participantId, raceId)).thenReturn(Optional.of(participant));

        participantService.removeParticipant(raceId, participantId, ownerId);

        verify(participantRepository).delete(participant);
    }
}
