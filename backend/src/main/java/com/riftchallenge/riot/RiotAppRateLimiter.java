package com.riftchallenge.riot;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

/**
 * Proactively throttles outgoing Riot API calls to the app-wide rate limit Riot itself reports on
 * every response via {@code X-App-Rate-Limit} (e.g. {@code "20:1,100:120"} = 20 req/1s AND 100
 * req/120s). Starts out matching the standard personal/dev key limits, then rebuilds its buckets
 * the moment a response reports different numbers — so swapping in a production key with a much
 * higher quota widens the throttle automatically, with no config change or redeploy needed.
 */
@Component
public class RiotAppRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RiotAppRateLimiter.class);
    private static final Pattern LIMIT_PAIR = Pattern.compile("(\\d+):(\\d+)");
    private static final String DEFAULT_LIMIT_HEADER = "20:1,100:120";

    private final AtomicReference<String> observedLimitHeader = new AtomicReference<>(DEFAULT_LIMIT_HEADER);
    private volatile Bucket bucket = buildBucket(DEFAULT_LIMIT_HEADER);

    public void acquire() throws InterruptedException {
        bucket.asBlocking().consume(1);
    }

    public void onResponseHeaders(HttpHeaders headers) {
        String limitHeader = headers.getFirst("X-App-Rate-Limit");
        if (limitHeader == null || limitHeader.isBlank() || limitHeader.equals(observedLimitHeader.get())) {
            return;
        }

        Bucket rebuilt = buildBucket(limitHeader);
        if (rebuilt == null) {
            return;
        }

        observedLimitHeader.set(limitHeader);
        bucket = rebuilt;
        log.info("Riot app rate limit updated to {}", limitHeader);
    }

    private static Bucket buildBucket(String limitHeader) {
        Matcher matcher = LIMIT_PAIR.matcher(limitHeader);
        var builder = Bucket.builder();
        boolean anyBandwidth = false;

        while (matcher.find()) {
            int max = Integer.parseInt(matcher.group(1));
            int windowSeconds = Integer.parseInt(matcher.group(2));
            if (max <= 0 || windowSeconds <= 0) {
                continue;
            }
            builder.addLimit(Bandwidth.classic(max, Refill.greedy(max, Duration.ofSeconds(windowSeconds))));
            anyBandwidth = true;
        }

        if (!anyBandwidth) {
            log.warn("Could not parse Riot rate limit header '{}', keeping the previous limit", limitHeader);
            return null;
        }
        return builder.build();
    }
}
