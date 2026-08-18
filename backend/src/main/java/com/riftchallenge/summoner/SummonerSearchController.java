package com.riftchallenge.summoner;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summoners")
public class SummonerSearchController {

    private final SummonerSearchService summonerSearchService;
    private final SummonerResolveService summonerResolveService;

    public SummonerSearchController(
            SummonerSearchService summonerSearchService,
            SummonerResolveService summonerResolveService
    ) {
        this.summonerSearchService = summonerSearchService;
        this.summonerResolveService = summonerResolveService;
    }

    @GetMapping("/search")
    public List<SummonerSuggestionResponse> search(@RequestParam String q) {
        return summonerSearchService.search(q);
    }

    @GetMapping("/resolve")
    public SummonerSuggestionResponse resolve(@RequestParam String riotId) {
        return summonerResolveService.resolve(riotId);
    }
}
