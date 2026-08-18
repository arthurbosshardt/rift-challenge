package com.riftchallenge.riot;

import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RiotMatchLookupService {

    private final RiotMatchClient riotMatchClient;
    private final ThreadLocal<Map<String, RiotMatchDetailDto>> refreshScopeCache = new ThreadLocal<>();

    public RiotMatchLookupService(RiotMatchClient riotMatchClient) {
        this.riotMatchClient = riotMatchClient;
    }

    public void beginRefreshScope() {
        refreshScopeCache.set(new HashMap<>());
    }

    public void endRefreshScope() {
        refreshScopeCache.remove();
    }

    public RiotMatchDetailDto getMatch(String matchId) {
        Map<String, RiotMatchDetailDto> cache = refreshScopeCache.get();
        if (cache == null) {
            return riotMatchClient.getMatch(matchId);
        }

        return cache.computeIfAbsent(matchId, riotMatchClient::getMatch);
    }
}
