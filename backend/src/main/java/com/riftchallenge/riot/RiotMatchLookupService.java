package com.riftchallenge.riot;

import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class RiotMatchLookupService {

    private final RiotMatchClient riotMatchClient;
    private final ThreadLocal<Map<String, RiotMatchDetailDto>> refreshScopeCache = new ThreadLocal<>();

    public RiotMatchLookupService(RiotMatchClient riotMatchClient) {
        this.riotMatchClient = riotMatchClient;
    }

    public void beginRefreshScope() {
        refreshScopeCache.set(new ConcurrentHashMap<>());
    }

    public void endRefreshScope() {
        refreshScopeCache.remove();
    }

    /** Lets a refresh fan out its cache to worker threads via {@link #bindScope}. */
    public Map<String, RiotMatchDetailDto> currentScopeCache() {
        return refreshScopeCache.get();
    }

    public void bindScope(Map<String, RiotMatchDetailDto> cache) {
        refreshScopeCache.set(cache);
    }

    public void unbindScope() {
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
