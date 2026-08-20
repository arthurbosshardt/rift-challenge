package com.riftchallenge.challenge;

import com.riftchallenge.authentication.AuthenticatedUserIds;
import com.riftchallenge.challenge.dto.AccountRecentGamesResponse;
import com.riftchallenge.challenge.dto.AddDuoRequest;
import com.riftchallenge.challenge.dto.AddParticipantRequest;
import com.riftchallenge.challenge.dto.CreateChallengeRequest;
import com.riftchallenge.challenge.dto.ParticipantResponse;
import com.riftchallenge.challenge.dto.ChallengeDetailResponse;
import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import com.riftchallenge.challenge.dto.UpdateChallengeEndRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeNameRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeScheduleRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeStartRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeVisibilityRequest;
import com.riftchallenge.match.MatchDetailService;
import com.riftchallenge.match.dto.MatchDetailResponse;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;
    private final ChallengeParticipantService participantService;
    private final ChallengeDuoService duoService;
    private final ChallengeRefreshRequestThrottle refreshRequestThrottle;
    private final RecentActivityService recentActivityService;
    private final RecentActivityRequestThrottle recentActivityRequestThrottle;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeDuoRepository duoRepository;
    private final ChallengeParticipantMatchRepository participantMatchRepository;
    private final MatchDetailService matchDetailService;

    public ChallengeController(
            ChallengeService challengeService,
            ChallengeParticipantService participantService,
            ChallengeDuoService duoService,
            ChallengeRefreshRequestThrottle refreshRequestThrottle,
            RecentActivityService recentActivityService,
            RecentActivityRequestThrottle recentActivityRequestThrottle,
            ChallengeParticipantRepository participantRepository,
            ChallengeDuoRepository duoRepository,
            ChallengeParticipantMatchRepository participantMatchRepository,
            MatchDetailService matchDetailService
    ) {
        this.challengeService = challengeService;
        this.participantService = participantService;
        this.duoService = duoService;
        this.refreshRequestThrottle = refreshRequestThrottle;
        this.recentActivityService = recentActivityService;
        this.recentActivityRequestThrottle = recentActivityRequestThrottle;
        this.participantRepository = participantRepository;
        this.duoRepository = duoRepository;
        this.participantMatchRepository = participantMatchRepository;
        this.matchDetailService = matchDetailService;
    }

    @GetMapping("/public")
    public List<ChallengeSummaryResponse> listPublicChallenges(
            @RequestParam(required = false) String challengeName,
            @RequestParam(required = false) String summoner,
            @RequestParam(required = false) ChallengeType type
    ) {
        return challengeService.listPublicChallenges(challengeName, summoner, type);
    }

    @GetMapping("/owned")
    public List<ChallengeSummaryResponse> listOwnedChallenges(Authentication authentication) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.listOwnedChallenges(ownerId);
    }

    @GetMapping("/participating")
    public List<ChallengeSummaryResponse> listParticipatingChallenges(Authentication authentication) {
        UUID userId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.listParticipatingChallenges(userId);
    }

    @GetMapping("/mine")
    public List<ChallengeSummaryResponse> listMyChallenges(Authentication authentication) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.listOwnedChallenges(ownerId);
    }

    @GetMapping("/share/{shareSlug}")
    public ChallengeDetailResponse getByShareSlug(
            @PathVariable String shareSlug,
            Authentication authentication
    ) {
        UUID callerId = AuthenticatedUserIds.optionalOwnerId(authentication);
        return challengeService.getByShareSlug(shareSlug, callerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChallengeDetailResponse createChallenge(
            Authentication authentication,
            @Valid @RequestBody CreateChallengeRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.createChallenge(ownerId, request);
    }

    @PatchMapping("/{challengeId}")
    public ChallengeDetailResponse updateChallenge(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @Valid @RequestBody UpdateChallengeRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.updateChallenge(challengeId, ownerId, request);
    }

    @PatchMapping("/{challengeId}/schedule")
    public ChallengeDetailResponse updateSchedule(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @Valid @RequestBody UpdateChallengeScheduleRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.updateSchedule(challengeId, ownerId, request);
    }

    @PatchMapping("/{challengeId}/start")
    public ChallengeDetailResponse updateStartAt(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @Valid @RequestBody UpdateChallengeStartRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.updateStartAt(challengeId, ownerId, request);
    }

    @PatchMapping("/{challengeId}/end")
    public ChallengeDetailResponse updateEndAt(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @Valid @RequestBody UpdateChallengeEndRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.updateEndAt(challengeId, ownerId, request);
    }

    @PatchMapping("/{challengeId}/visibility")
    public ChallengeDetailResponse updateVisibility(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @Valid @RequestBody UpdateChallengeVisibilityRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.updateVisibility(challengeId, ownerId, request);
    }

    @PatchMapping("/{challengeId}/name")
    public ChallengeDetailResponse updateName(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @Valid @RequestBody UpdateChallengeNameRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return challengeService.updateName(challengeId, ownerId, request);
    }

    @DeleteMapping("/{challengeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteChallenge(
            Authentication authentication,
            @PathVariable UUID challengeId
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        challengeService.deleteChallenge(challengeId, ownerId);
    }

    @PostMapping("/{challengeId}/refresh")
    public ChallengeDetailResponse refreshChallenge(
            @PathVariable UUID challengeId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        UUID callerId = AuthenticatedUserIds.optionalOwnerId(authentication);
        refreshRequestThrottle.enforce(request, callerId);
        return challengeService.refreshChallenge(challengeId, callerId);
    }

    @PostMapping("/{challengeId}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipantResponse addParticipant(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @Valid @RequestBody AddParticipantRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return participantService.addParticipant(challengeId, ownerId, request);
    }

    @PostMapping("/{challengeId}/duos")
    @ResponseStatus(HttpStatus.CREATED)
    public void addDuo(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @Valid @RequestBody AddDuoRequest request
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        duoService.addDuo(challengeId, ownerId, request);
    }

    @DeleteMapping("/{challengeId}/duos/{duoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDuo(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @PathVariable UUID duoId
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        duoService.removeDuo(challengeId, duoId, ownerId);
    }

    @DeleteMapping("/{challengeId}/participants/{participantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(
            Authentication authentication,
            @PathVariable UUID challengeId,
            @PathVariable UUID participantId
    ) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        participantService.removeParticipant(challengeId, participantId, ownerId);
    }

    @GetMapping("/recent")
    public List<AccountRecentGamesResponse> listRecentGames(Authentication authentication) {
        UUID userId = AuthenticatedUserIds.requireOwnerId(authentication);
        recentActivityRequestThrottle.enforce(userId);
        return recentActivityService.listRecentGames(userId);
    }

    @GetMapping("/{challengeId}/participants/{participantId}/matches/{matchId}")
    public MatchDetailResponse getParticipantMatchDetail(
            @PathVariable UUID challengeId,
            @PathVariable UUID participantId,
            @PathVariable String matchId,
            Authentication authentication
    ) {
        UUID callerId = AuthenticatedUserIds.optionalOwnerId(authentication);
        challengeService.requireShareAccessById(challengeId, callerId);
        ChallengeParticipant participant = participantRepository.findByIdAndChallengeId(participantId, challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));
        if (!participantMatchRepository.existsByParticipantIdAndRiotMatchId(participantId, matchId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
        }
        return matchDetailService.buildMatchDetail(matchId, participant.getRiotPuuid());
    }

    @GetMapping("/{challengeId}/duos/{duoId}/matches/{matchId}")
    public MatchDetailResponse getDuoMatchDetail(
            @PathVariable UUID challengeId,
            @PathVariable UUID duoId,
            @PathVariable String matchId,
            Authentication authentication
    ) {
        UUID callerId = AuthenticatedUserIds.optionalOwnerId(authentication);
        challengeService.requireShareAccessById(challengeId, callerId);
        duoRepository.findByIdAndChallengeId(duoId, challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Duo not found"));
        List<ChallengeParticipant> duoPlayers = participantRepository.findByDuoIdOrderByCreatedAtAsc(duoId);
        ChallengeParticipant focusPlayer = duoPlayers.stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Duo has no players"));
        boolean matchLinked = duoPlayers.stream()
                .anyMatch(player -> participantMatchRepository.existsByParticipantIdAndRiotMatchId(player.getId(), matchId));
        if (!matchLinked) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
        }
        return matchDetailService.buildMatchDetail(matchId, focusPlayer.getRiotPuuid());
    }
}
