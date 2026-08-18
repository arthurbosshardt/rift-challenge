package com.riftchallenge.summoner;

import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotIdParser;
import com.riftchallenge.riot.dto.RiotAccountDto;
import org.springframework.stereotype.Service;

@Service
public class SummonerResolveService {

    private final RiotAccountClient riotAccountClient;

    public SummonerResolveService(RiotAccountClient riotAccountClient) {
        this.riotAccountClient = riotAccountClient;
    }

    public SummonerSuggestionResponse resolve(String riotId) {
        RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse(riotId);
        RiotAccountDto account = riotAccountClient.getAccountByRiotId(parsed.gameName(), parsed.tagLine());
        return new SummonerSuggestionResponse(
                account.puuid(),
                account.gameName(),
                account.tagLine(),
                account.gameName() + "#" + account.tagLine(),
                null
        );
    }
}
