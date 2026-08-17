package com.riftrace.race;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftrace.account.AppUserRepository;
import com.riftrace.account.UserRiotAccountService;
import com.riftrace.race.dto.CreateRaceRequest;
import com.riftrace.race.dto.RaceSummaryResponse;
import com.riftrace.race.dto.UpdateRaceEndRequest;
import com.riftrace.race.dto.UpdateRaceScheduleRequest;
import com.riftrace.race.dto.UpdateRaceStartRequest;
import com.riftrace.race.dto.UpdateRaceNameRequest;
import com.riftrace.race.dto.UpdateRaceVisibilityRequest;
import com.riftrace.riot.dto.RiotAccountDto;
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

    @Mock
    private UserRiotAccountService userRiotAccountService;

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
                userRiotAccountService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void listPublicRaces_withSearch_filtersByRaceName() {
        Race matchingRace = Race.create(
                UUID.randomUUID(),
                "Les petits soldats",
                RaceType.SOLOQ,
                NOW.minusSeconds(60),
                true
        );
        Race otherRace = Race.create(
                UUID.randomUUID(),
                "Autre challenge",
                RaceType.SOLOQ,
                NOW.minusSeconds(120),
                true
        );
        when(raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(matchingRace, otherRace));
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(matchingRace.getId())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());

        List<RaceSummaryResponse> races = raceService.listPublicRaces("soldats", null, null);

        assertThat(races).hasSize(1);
        assertThat(races.getFirst().name()).isEqualTo("Les petits soldats");
    }

    @Test
    void listPublicRaces_withSearch_filtersByParticipant() {
        Race race = Race.create(
                UUID.randomUUID(),
                "Duo challenge",
                RaceType.SOLOQ,
                NOW.minusSeconds(60),
                true
        );
        when(raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(race));
        when(participantRepository.findDistinctPublicRaceIdsByParticipantSearch(NOW, "tanor"))
                .thenReturn(List.of(race.getId()));
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(race.getId())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());

        List<RaceSummaryResponse> races = raceService.listPublicRaces(null, "tanor", null);

        assertThat(races).hasSize(1);
        assertThat(races.getFirst().name()).isEqualTo("Duo challenge");
    }

    @Test
    void listPublicRaces_withShortSearch_ignoresSearchFilters() {
        Race race = Race.create(
                UUID.randomUUID(),
                "Les petits soldats",
                RaceType.SOLOQ,
                NOW.minusSeconds(60),
                true
        );
        when(raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(race));
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(race.getId())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());

        List<RaceSummaryResponse> races = raceService.listPublicRaces("so", "ta", null);

        assertThat(races).hasSize(1);
        verify(participantRepository, never()).findDistinctPublicRaceIdsByParticipantSearch(any(), any());
    }

    @Test
    void listPublicRaces_withTypeFilter_returnsOnlyMatchingType() {
        Race soloRace = Race.create(
                UUID.randomUUID(),
                "Solo race",
                RaceType.SOLOQ,
                NOW.minusSeconds(60),
                true
        );
        Race duoRace = Race.create(
                UUID.randomUUID(),
                "Duo race",
                RaceType.DUOQ,
                NOW.minusSeconds(120),
                true
        );
        when(raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(soloRace, duoRace));
        when(duoProgressService.buildProgress(duoRace.getId())).thenReturn(List.of());

        List<RaceSummaryResponse> races = raceService.listPublicRaces(null, null, RaceType.DUOQ);

        assertThat(races).hasSize(1);
        assertThat(races.getFirst().type()).isEqualTo(RaceType.DUOQ);
    }

    @Test
    void listPublicRaces_withRaceNameAndSummoner_appliesBothFilters() {
        Race matchingRace = Race.create(
                UUID.randomUUID(),
                "Les petits soldats",
                RaceType.SOLOQ,
                NOW.minusSeconds(60),
                true
        );
        Race otherRace = Race.create(
                UUID.randomUUID(),
                "Les petits soldats bis",
                RaceType.SOLOQ,
                NOW.minusSeconds(120),
                true
        );
        when(raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(matchingRace, otherRace));
        when(participantRepository.findDistinctPublicRaceIdsByParticipantSearch(NOW, "tanor"))
                .thenReturn(List.of(matchingRace.getId()));
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(matchingRace.getId())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());

        List<RaceSummaryResponse> races = raceService.listPublicRaces("soldats", "tanor", null);

        assertThat(races).hasSize(1);
        assertThat(races.getFirst().id()).isEqualTo(matchingRace.getId());
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
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(activeRace.getId())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());

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
    void updateSchedule_whenOwner_updatesBothDatesWithSingleRefresh() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.minusSeconds(7200);
        Instant newEnd = NOW.plusSeconds(86_400);
        Race race = Race.create(
                ownerId,
                "Test",
                RaceType.SOLOQ,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                false
        );
        UUID raceId = race.getId();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(any())).thenReturn(Optional.empty());
        when(raceSyncService.refreshRace(raceId)).thenReturn(NOW);

        var response = raceService.updateSchedule(
                raceId,
                ownerId,
                new UpdateRaceScheduleRequest(newStart, newEnd)
        );

        verify(raceSyncService).refreshRace(raceId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.endAt()).isEqualTo(newEnd);
    }

    @Test
    void updateVisibility_whenOwner_updatesIsPublic() {
        UUID ownerId = UUID.randomUUID();
        Race race = Race.create(
                ownerId,
                "Test",
                RaceType.SOLOQ,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                false
        );
        UUID raceId = race.getId();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(any())).thenReturn(Optional.empty());

        var response = raceService.updateVisibility(
                raceId,
                ownerId,
                new UpdateRaceVisibilityRequest(true)
        );

        assertThat(race.isPublic()).isTrue();
        assertThat(response.isPublic()).isTrue();
        verify(raceRepository).save(race);
    }

    @Test
    void updateName_whenOwner_updatesName() {
        UUID ownerId = UUID.randomUUID();
        Race race = Race.create(
                ownerId,
                "Old name",
                RaceType.SOLOQ,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                false
        );
        UUID raceId = race.getId();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(any())).thenReturn(Optional.empty());

        var response = raceService.updateName(raceId, ownerId, new UpdateRaceNameRequest("New name"));

        assertThat(race.getName()).isEqualTo("New name");
        assertThat(response.name()).isEqualTo("New name");
        verify(raceRepository).save(race);
    }

    @Test
    void updateStartAt_whenNotOwner_throwsForbidden() {
        UUID raceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Race race = Race.create(
                ownerId,
                "Test",
                RaceType.SOLOQ,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(7200),
                false
        );
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> raceService.updateStartAt(
                raceId,
                UUID.randomUUID(),
                new UpdateRaceStartRequest(NOW.plusSeconds(1800))
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("403");
    }

    @Test
    void updateStartAt_whenRaceAlreadyStarted_updatesAndRefreshes() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.minusSeconds(7200);
        Race race = Race.create(
                ownerId,
                "Test",
                RaceType.SOLOQ,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(3600),
                false
        );
        UUID raceId = race.getId();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(any())).thenReturn(Optional.empty());
        when(raceSyncService.refreshRace(raceId)).thenReturn(NOW);

        var response = raceService.updateStartAt(raceId, ownerId, new UpdateRaceStartRequest(newStart));

        verify(raceSyncService).refreshRace(raceId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void updateStartAt_whenOwnerBeforeStart_updatesStart() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.plusSeconds(7200);
        Race race = Race.create(
                ownerId,
                "Test",
                RaceType.SOLOQ,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(86_400),
                false
        );
        UUID raceId = race.getId();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(any())).thenReturn(Optional.empty());

        var response = raceService.updateStartAt(raceId, ownerId, new UpdateRaceStartRequest(newStart));

        verify(raceSyncService, never()).refreshRace(raceId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.status()).isEqualTo("NOT_STARTED");
    }

    @Test
    void updateStartAt_whenMovedToPast_autoRefreshesIfAvailable() {
        UUID ownerId = UUID.randomUUID();
        Instant newStart = NOW.minusSeconds(60);
        Race race = Race.create(
                ownerId,
                "Test",
                RaceType.SOLOQ,
                NOW.plusSeconds(3600),
                NOW.plusSeconds(86_400),
                false
        );
        UUID raceId = race.getId();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(any())).thenReturn(Optional.empty());
        when(raceSyncService.refreshRace(raceId)).thenReturn(NOW);

        var response = raceService.updateStartAt(raceId, ownerId, new UpdateRaceStartRequest(newStart));

        verify(raceSyncService).refreshRace(raceId);
        assertThat(response.startAt()).isEqualTo(newStart);
        assertThat(response.status()).isEqualTo("ACTIVE");
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
        UUID raceId = race.getId();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(any())).thenReturn(Optional.empty());
        when(raceSyncService.refreshRace(raceId)).thenReturn(NOW);

        var response = raceService.updateEndAt(raceId, ownerId, new UpdateRaceEndRequest(newEnd));

        verify(raceSyncService).refreshRace(raceId);
        assertThat(response.endAt()).isEqualTo(newEnd);
        assertThat(response.isOwner()).isTrue();
    }

    @Test
    void updateEndAt_whenRefreshOnCooldown_skipsSync() {
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
        UUID raceId = race.getId();
        RaceRefresh recentRefresh = RaceRefresh.create(raceId, NOW.minusSeconds(30));

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(raceRepository.save(race)).thenReturn(race);
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(any())).thenReturn(List.of());
        when(progressService.buildProgress(List.of())).thenReturn(List.of());
        when(raceRefreshRepository.findByRaceId(raceId)).thenReturn(Optional.of(recentRefresh));

        var response = raceService.updateEndAt(raceId, ownerId, new UpdateRaceEndRequest(newEnd));

        verify(raceSyncService, never()).refreshRace(raceId);
        assertThat(response.endAt()).isEqualTo(newEnd);
        assertThat(response.refreshAvailable()).isFalse();
    }

    @Test
    void listParticipatingRaces_returnsRacesForLinkedAccounts() {
        UUID userId = UUID.randomUUID();
        UUID raceId = UUID.randomUUID();
        Race race = Race.create(userId, "Joined", RaceType.DUOQ, NOW.minusSeconds(3600), NOW.plusSeconds(3600), false);

        when(userRiotAccountService.listLinkedPuids(userId)).thenReturn(List.of("puuid-1"));
        when(participantRepository.findDistinctRaceIdsByRiotPuuidIn(List.of("puuid-1"))).thenReturn(List.of(raceId));
        when(raceRepository.findByIdInOrderByStartAtDesc(List.of(raceId))).thenReturn(List.of(race));
        when(duoProgressService.buildProgress(any())).thenReturn(List.of());

        List<RaceSummaryResponse> races = raceService.listParticipatingRaces(userId);

        assertThat(races).hasSize(1);
        assertThat(races.getFirst().name()).isEqualTo("Joined");
    }

    @Test
    void listParticipatingRaces_withoutLinkedAccounts_returnsEmpty() {
        UUID userId = UUID.randomUUID();
        when(userRiotAccountService.listLinkedPuids(userId)).thenReturn(List.of());

        assertThat(raceService.listParticipatingRaces(userId)).isEmpty();
    }

    @Test
    void listPublicRaces_includesParticipantGameNamesForSoloRace() {
        Race race = Race.create(
                UUID.randomUUID(),
                "Les petits soldats",
                RaceType.SOLOQ,
                NOW.minusSeconds(60),
                true
        );
        RaceParticipant tanor = RaceParticipant.create(
                race.getId(),
                new RiotAccountDto("puuid-1", "Tanor", "7154")
        );
        RaceParticipant kaori = RaceParticipant.create(
                race.getId(),
                new RiotAccountDto("puuid-2", "Kaori", "EUW33")
        );

        when(raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(race));
        when(participantRepository.findByRaceIdOrderByCreatedAtAsc(race.getId()))
                .thenReturn(List.of(tanor, kaori));
        when(progressService.buildProgress(List.of(tanor, kaori))).thenReturn(List.of());

        List<RaceSummaryResponse> races = raceService.listPublicRaces(null, null, null);

        assertThat(races).hasSize(1);
        assertThat(races.getFirst().entryCount()).isEqualTo(2);
        assertThat(races.getFirst().participantGameNames()).containsExactly("Tanor", "Kaori");
    }
}
