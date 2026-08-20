package com.riftchallenge.challenge;

import com.riftchallenge.common.ClientRequestKeys;
import com.riftchallenge.common.IntervalRequestThrottle;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ChallengeListRefreshRequestThrottle {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(5);

    private final IntervalRequestThrottle throttle;

    public ChallengeListRefreshRequestThrottle(Clock clock) {
        this.throttle = new IntervalRequestThrottle(
                clock,
                MIN_INTERVAL,
                "Please wait before refreshing again"
        );
    }

    public void enforce(HttpServletRequest request, UUID callerId) {
        throttle.enforce(ClientRequestKeys.resolve(request, callerId));
    }

    // Visible for tests
    void enforce(String clientKey) {
        throttle.enforce(clientKey);
    }
}
