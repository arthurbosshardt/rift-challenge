package com.riftchallenge.leaderboard;

import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The app's only passive/scheduled refresh: syncs every linked Riot account and recomputes the
 * leaderboard 4x/day (see {@link LeaderboardCacheService#refresh}), never on user request.
 * {@code @Lazy(false)} overrides {@code spring.main.lazy-initialization=true} — without it this
 * bean (and its cron trigger) would never be created since nothing else references it.
 */
@Component
@Lazy(false)
public class LeaderboardRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(LeaderboardRefreshScheduler.class);

    private final LeaderboardCacheService cacheService;
    private final Clock clock;

    public LeaderboardRefreshScheduler(LeaderboardCacheService cacheService, Clock clock) {
        this.cacheService = cacheService;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 0,6,12,18 * * *", zone = "UTC")
    public void refresh() {
        log.info("Refreshing leaderboard cache");
        cacheService.refresh(clock.instant());
        log.info("Leaderboard cache refreshed");
    }
}
