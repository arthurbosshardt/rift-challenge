package com.riftrace.race;

import com.riftrace.authentication.AuthenticatedUserIds;
import com.riftrace.race.dto.AddDuoRequest;
import com.riftrace.race.dto.AddParticipantRequest;
import com.riftrace.race.dto.CreateRaceRequest;
import com.riftrace.race.dto.ParticipantResponse;
import com.riftrace.race.dto.RaceDetailResponse;
import com.riftrace.race.dto.RaceSummaryResponse;
import com.riftrace.race.dto.UpdateRaceEndRequest;
import com.riftrace.race.dto.UpdateRaceScheduleRequest;
import com.riftrace.race.dto.UpdateRaceStartRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/races")
public class RaceController {

    private final RaceService raceService;
    private final RaceParticipantService participantService;
    private final RaceDuoService duoService;

    public RaceController(
            RaceService raceService,
            RaceParticipantService participantService,
            RaceDuoService duoService
    ) {
        this.raceService = raceService;
        this.participantService = participantService;
        this.duoService = duoService;
    }

    @GetMapping("/public")
    public List<RaceSummaryResponse> listPublicRaces() {
        return raceService.listPublicRaces();
    }

    @GetMapping("/owned")
    public List<RaceSummaryResponse> listOwnedRaces(Authentication authentication) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return raceService.listOwnedRaces(ownerId);
    }

    @GetMapping("/participating")
    public List<RaceSummaryResponse> listParticipatingRaces(Authentication authentication) {
        UUID userId = AuthenticatedUserIds.requireOwnerId(authentication);
        return raceService.listParticipatingRaces(userId);
    }

    @GetMapping("/mine")
    public List<RaceSummaryResponse> listMyRaces(Authentication authentication) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return raceService.listOwnedRaces(ownerId);
    }

    @GetMapping("/share/{shareSlug}")
    public RaceDetailResponse getByShareSlug(
            @PathVariable UUID shareSlug,
            Authentication authentication
    ) {
        UUID callerId = AuthenticatedUserIds.optionalOwnerId(authentication);
        return raceService.getByShareSlug(shareSlug, callerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RaceDetailResponse createRace(
            Authentication authentication,
            @Valid @RequestBody CreateRaceRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return raceService.createRace(ownerId, request);
    }

    @PatchMapping("/{raceId}/schedule")
    public RaceDetailResponse updateSchedule(
            Authentication authentication,
            @PathVariable UUID raceId,
            @Valid @RequestBody UpdateRaceScheduleRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return raceService.updateSchedule(raceId, ownerId, request);
    }

    @PatchMapping("/{raceId}/start")
    public RaceDetailResponse updateStartAt(
            Authentication authentication,
            @PathVariable UUID raceId,
            @Valid @RequestBody UpdateRaceStartRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return raceService.updateStartAt(raceId, ownerId, request);
    }

    @PatchMapping("/{raceId}/end")
    public RaceDetailResponse updateEndAt(
            Authentication authentication,
            @PathVariable UUID raceId,
            @Valid @RequestBody UpdateRaceEndRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return raceService.updateEndAt(raceId, ownerId, request);
    }

    @PostMapping("/{raceId}/refresh")
    public RaceDetailResponse refreshRace(
            @PathVariable UUID raceId,
            Authentication authentication
    ) {
        UUID callerId = AuthenticatedUserIds.optionalOwnerId(authentication);
        return raceService.refreshRace(raceId, callerId);
    }

    @PostMapping("/{raceId}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipantResponse addParticipant(
            Authentication authentication,
            @PathVariable UUID raceId,
            @Valid @RequestBody AddParticipantRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return participantService.addParticipant(raceId, ownerId, request);
    }

    @PostMapping("/{raceId}/duos")
    @ResponseStatus(HttpStatus.CREATED)
    public void addDuo(
            Authentication authentication,
            @PathVariable UUID raceId,
            @Valid @RequestBody AddDuoRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        duoService.addDuo(raceId, ownerId, request);
    }

    @DeleteMapping("/{raceId}/duos/{duoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDuo(
            Authentication authentication,
            @PathVariable UUID raceId,
            @PathVariable UUID duoId
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        duoService.removeDuo(raceId, duoId, ownerId);
    }

    @DeleteMapping("/{raceId}/participants/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(
            Authentication authentication,
            @PathVariable UUID raceId,
            @PathVariable UUID participantId
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        participantService.removeParticipant(raceId, participantId, ownerId);
    }
}
