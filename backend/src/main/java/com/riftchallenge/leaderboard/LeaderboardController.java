package com.riftchallenge.leaderboard;

import com.riftchallenge.account.AppUser;
import com.riftchallenge.account.AppUserRepository;
import com.riftchallenge.authentication.AuthenticatedUserIds;
import com.riftchallenge.leaderboard.dto.LeaderboardResponse;
import com.riftchallenge.leaderboard.dto.LeaderboardSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    private final LeaderboardCacheService cacheService;
    private final AppUserRepository appUserRepository;
    private final LeaderboardProperties properties;
    private final Clock clock;

    public LeaderboardController(
            LeaderboardCacheService cacheService,
            AppUserRepository appUserRepository,
            LeaderboardProperties properties,
            Clock clock
    ) {
        this.cacheService = cacheService;
        this.appUserRepository = appUserRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @GetMapping
    public LeaderboardResponse getLeaderboard(Authentication authentication) {
        LeaderboardSnapshot snapshot = cacheService.readOrBootstrap();
        Instant now = clock.instant();
        return LeaderboardResponse.from(
                snapshot,
                LeaderboardCacheService.nextRefreshAt(now),
                isAdmin(authentication),
                properties.seasonStartAt()
        );
    }

    /** Manual refresh, restricted to a single admin account. No Riot calls here — pure DB aggregation. */
    @PostMapping("/refresh")
    public LeaderboardResponse refresh(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to refresh the leaderboard");
        }

        Instant now = clock.instant();
        LeaderboardSnapshot snapshot = cacheService.refresh(now);
        return LeaderboardResponse.from(snapshot, LeaderboardCacheService.nextRefreshAt(now), true, properties.seasonStartAt());
    }

    private boolean isAdmin(Authentication authentication) {
        UUID userId = AuthenticatedUserIds.optionalOwnerId(authentication);
        if (userId == null || properties.adminEmail() == null) {
            return false;
        }
        return appUserRepository.findById(userId)
                .map(AppUser::getEmail)
                .map(email -> email.equalsIgnoreCase(properties.adminEmail()))
                .orElse(false);
    }
}
