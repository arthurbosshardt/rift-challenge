package com.riftchallenge.summoner;

import com.riftchallenge.authentication.AuthenticatedUserIds;
import com.riftchallenge.common.ClientRequestKeys;
import com.riftchallenge.common.IntervalRequestThrottle;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Guards the live-Riot fallback in {@link SummonerSearchService} — the typeahead re-queries on
 * every debounced keystroke, so without this a user typing out a full Riot ID one character at a
 * time could trigger one Riot call per tagLine character. Unlike other throttles in this app,
 * rejection here is silent ({@link #tryClaim} returns a boolean, never throws): an automatic,
 * passive fallback should just yield no suggestion for that tick, not fail the whole search.
 */
@Component
public class SummonerSearchRiotFallbackThrottle {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(2);

    private final IntervalRequestThrottle throttle;

    public SummonerSearchRiotFallbackThrottle(Clock clock) {
        this.throttle = new IntervalRequestThrottle(clock, MIN_INTERVAL, "Please wait before searching again");
    }

    public boolean tryClaim(HttpServletRequest request, Authentication authentication) {
        String clientKey = ClientRequestKeys.resolve(request, AuthenticatedUserIds.optionalOwnerId(authentication));
        try {
            throttle.enforce(clientKey);
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }
}
