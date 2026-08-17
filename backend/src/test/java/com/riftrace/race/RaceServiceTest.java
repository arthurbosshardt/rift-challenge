package com.riftrace.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.riftrace.account.AppUserRepository;
import com.riftrace.race.dto.CreateRaceRequest;
import com.riftrace.race.dto.RaceSummaryResponse;
import com.riftrace.race.dto.UpdateRaceEndRequest;
import com.riftrace.synchronization.RaceSyncService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RaceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");

    @Mock
    private RaceRepository raceRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RaceParticipantRepository participantRepository;

    @Mock
    private RaceProgressService progressService;

    @Mock
    private RaceDuoProgressService duoProgressService;

    @Mock
    private ParticipantProfileService participantProfileService;

    @Mock
    private RaceRefreshRepository raceRefreshRepository;

    @Mock
    private RaceSyncService raceSyncService;

    private RaceService raceService;

    @BeforeEach
    void setUp() {
        raceService = new RaceService(
                raceRepository,
                appUserRepository,
                participantRepository,
                progressService,
                duoProgressService,
                participantProfileService,
                raceRefreshRepository,
                raceSyncService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void listPublicRaces_returnsOnlyStartedPublicRaces() {
        Race activeRace = Race.create(
                UUID.randomUUID(),
                "Active",
                RaceType.SOLOQ,
                NOW.minusSeconds(60),
                true
        );
        when(raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(activeRace));

        List<RaceSummaryResponse> races = raceService.listPublicRaces();

        assertThat(races).hasSize(1);
        assertThat(races.getFirst().status()).isEqualTo("ACTIVE");
    }

    @Test
    void createRace_rejectsEndBeforeStart() {
        UUID ownerId = UUID.randomUUID();
        when(appUserRepository.existsById(ownerId)).thenReturn(true);

        assertThatThrownBy(() -> raceService.createRace(
                ownerId,
                new CreateRaceRequest(
                        "Race",
                        RaceType.SOLOQ,
                        NOW,
                        NOW.minusSeconds(60),
                        false
                )
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void updateEndAt_whenNotOwner_throwsForbidden() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Race race = Race.create(
                ownerId,
                "Test",
                RaceType.SOLOQ,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                false
        );
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> raceService.updateEndAt(
                raceId,
                UUID.randomUUID(),
                new UpdateRaceEndRequest(NOW.plusSeconds(7200))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void updateEndAt_whenOwner_updatesEnd() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant newEnd = NOW.plusSeconds(86_400);
        Race race = Race.create(
                ownerId,
                "Test",
                RaceType.SOLOQ,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                false
        );
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(any())).thenReturn(Optional.empty());

        var response = raceService.updateEndAt(raceId, ownerId, new UpdateRaceEndRequest(newEnd));

        assertThat(response.endAt()).isEqualTo(newEnd);
        assertThat(response.isOwner()).isTrue();
    }
}
