package com.riftchallenge.player;

import com.riftchallenge.challenge.ChallengeService;
import com.riftchallenge.challenge.RecentActivityService;
import com.riftchallenge.challenge.dto.AccountRecentGamesResponse;
import com.riftchallenge.challenge.dto.ChallengeListResponse;
import com.riftchallenge.summoner.PlayerLookupService;
import com.riftchallenge.summoner.SummonerSuggestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public, unauthenticated player profile — backs the shareable "search any player" page.
 * Reuses the same underlying data as the authenticated "my account" endpoints
 * ({@link RecentActivityService}, {@link ChallengeService}), just resolved from a riotId
 * instead of the caller's own linked account.
 */
@RestController
@RequestMapping("/api/players")
public class PlayerProfileController {

    private final PlayerLookupService playerLookupService;
    private final RecentActivityService recentActivityService;
    private final ChallengeService challengeService;
    private final PlayerProfileRequestThrottle throttle;

    public PlayerProfileController(
            PlayerLookupService playerLookupService,
            RecentActivityService recentActivityService,
            ChallengeService challengeService,
            PlayerProfileRequestThrottle throttle
    ) {
        this.playerLookupService = playerLookupService;
        this.recentActivityService = recentActivityService;
        this.challengeService = challengeService;
        this.throttle = throttle;
    }

    @GetMapping("/{riotId}")
    public SummonerSuggestionResponse resolve(
            HttpServletRequest request,
            Authentication authentication,
            @PathVariable String riotId
    ) {
        throttle.enforce(request, authentication, "resolve");
        ExactRiotId parsed = ExactRiotId.parse(riotId);
        return playerLookupService.resolve(parsed.gameName(), parsed.tagLine())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));
    }

    @GetMapping("/{riotId}/activity")
    public AccountRecentGamesResponse getActivity(
            HttpServletRequest request,
            Authentication authentication,
            @PathVariable String riotId
    ) {
        throttle.enforce(request, authentication, "activity");
        ExactRiotId parsed = ExactRiotId.parse(riotId);
        return recentActivityService.getActivityForRiotId(parsed.gameName(), parsed.tagLine())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));
    }

    @GetMapping("/{riotId}/challenges")
    public ChallengeListResponse getChallenges(
            HttpServletRequest request,
            Authentication authentication,
            @PathVariable String riotId
    ) {
        throttle.enforce(request, authentication, "challenges");
        ExactRiotId parsed = ExactRiotId.parse(riotId);
        SummonerSuggestionResponse player = playerLookupService.resolve(parsed.gameName(), parsed.tagLine())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Player not found"));
        return challengeService.listChallengesForPuuids(List.of(player.puuid()));
    }

    /**
     * Splits an already-correct riotId (from our own search suggestions or a shared URL) into
     * gameName/tagLine without normalizing whitespace — unlike {@code RiotIdParser}, which is
     * built for sloppy user-typed input, a stored gameName can legitimately contain a single
     * internal space and stripping it would break the exact-match DB lookup.
     */
    private record ExactRiotId(String gameName, String tagLine) {
        static ExactRiotId parse(String riotId) {
            int hashIndex = riotId == null ? -1 : riotId.indexOf('#');
            if (hashIndex <= 0 || hashIndex >= riotId.length() - 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Riot ID must be in gameName#tagLine format");
            }
            return new ExactRiotId(riotId.substring(0, hashIndex), riotId.substring(hashIndex + 1));
        }
    }
}
