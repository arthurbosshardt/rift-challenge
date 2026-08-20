package com.riftchallenge.challenge;

import com.riftchallenge.common.ClientRequestKeys;
import com.riftchallenge.common.IntervalRequestThrottle;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class OpenGraphRequestThrottle {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(1);

    private final IntervalRequestThrottle throttle;

    public OpenGraphRequestThrottle(Clock clock) {
        this.throttle = new IntervalRequestThrottle(
                clock,
                MIN_INTERVAL,
                "Please wait before requesting another preview"
        );
    }

    public void enforce(HttpServletRequest request, String resource) {
        throttle.enforce(resource + ":" + ClientRequestKeys.resolve(request, null));
    }
}
