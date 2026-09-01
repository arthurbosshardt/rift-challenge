package com.riftchallenge.summoner;

import com.riftchallenge.authentication.AuthenticatedUserIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summoners")
public class SummonerSearchController {

    private final SummonerSearchService summonerSearchService;
    private final SummonerResolveService summonerResolveService;
    private final SummonerResolveRequestThrottle summonerResolveRequestThrottle;

    public SummonerSearchController(
            SummonerSearchService summonerSearchService,
            SummonerResolveService summonerResolveService,
            SummonerResolveRequestThrottle summonerResolveRequestThrottle
    ) {
        this.summonerSearchService = summonerSearchService;
        this.summonerResolveService = summonerResolveService;
        this.summonerResolveRequestThrottle = summonerResolveRequestThrottle;
    }

    @GetMapping("/search")
    public List<SummonerSuggestionResponse> search(
            HttpServletRequest request,
            Authentication authentication,
            @RequestParam String q
    ) {
        return summonerSearchService.search(q, request, authentication);
    }

    @GetMapping("/resolve")
    public SummonerSuggestionResponse resolve(Authentication authentication, @RequestParam String riotId) {
        UUID userId = AuthenticatedUserIds.requireOwnerId(authentication);
        summonerResolveRequestThrottle.enforce(userId);
        return summonerResolveService.resolve(riotId);
    }
}
