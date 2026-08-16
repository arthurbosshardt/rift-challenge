package com.riftrace.race;

import com.riftrace.authentication.AuthenticatedUserIds;
import com.riftrace.race.dto.AddParticipantRequest;
import com.riftrace.race.dto.CreateRaceRequest;
import com.riftrace.race.dto.ParticipantResponse;
import com.riftrace.race.dto.RaceDetailResponse;
import com.riftrace.race.dto.RaceSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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

    public RaceController(RaceService raceService, RaceParticipantService participantService) {
        this.raceService = raceService;
        this.participantService = participantService;
    }

    @GetMapping("/public")
    public List<RaceSummaryResponse> listPublicRaces() {
        return raceService.listPublicRaces();
    }

    @GetMapping("/mine")
    public List<RaceSummaryResponse> listMyRaces(Authentication authentication) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return raceService.listMyRaces(ownerId);
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
