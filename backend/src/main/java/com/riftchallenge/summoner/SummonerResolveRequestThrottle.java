package com.riftchallenge.summoner;

import com.riftchallenge.common.IntervalRequestThrottle;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SummonerResolveRequestThrottle {

    static final Duration MIN_INTERVAL = Duration.ofSeconds(2);

    private final IntervalRequestThrottle throttle;

    public SummonerResolveRequestThrottle(Clock clock) {
        this.throttle = new IntervalRequestThrottle(
                clock,
                MIN_INTERVAL,
                "Please wait before resolving another summoner"
        );
    }

    public void enforce(UUID userId) {
        throttle.enforce("user:" + userId);
    }
}
