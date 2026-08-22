package com.riftchallenge.account;

import com.riftchallenge.account.AppUser;
import com.riftchallenge.account.dto.BulkRegisterRiotAccountsRequest;
import com.riftchallenge.account.dto.BulkRegisterRiotAccountsResponse;
import com.riftchallenge.authentication.AuthenticatedUserIds;
import com.riftchallenge.leaderboard.LeaderboardAccountSyncService;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/riot-accounts")
public class AdminRiotAccountController {

    private final RiotAccountService riotAccountService;
    private final AppUserRepository appUserRepository;
    private final LeaderboardAccountSyncService leaderboardAccountSyncService;
    private final LeaderboardProperties properties;
    private final Clock clock;

    public AdminRiotAccountController(
            RiotAccountService riotAccountService,
            AppUserRepository appUserRepository,
            LeaderboardAccountSyncService leaderboardAccountSyncService,
            LeaderboardProperties properties,
            Clock clock
    ) {
        this.riotAccountService = riotAccountService;
        this.appUserRepository = appUserRepository;
        this.leaderboardAccountSyncService = leaderboardAccountSyncService;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping("/bulk")
    public BulkRegisterRiotAccountsResponse registerBulk(
            @RequestBody BulkRegisterRiotAccountsRequest request,
            Authentication authentication
    ) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to register Riot accounts");
        }
        if (request.riotIds() == null || request.riotIds().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "riotIds must not be empty");
        }

        BulkRegisterRiotAccountsResponse response = riotAccountService.registerBulk(request);
        leaderboardAccountSyncService.syncAllAccounts(clock.instant());
        return response;
    }

    private boolean isAdmin(Authentication authentication) {
        UUID userId = AuthenticatedUserIds.optionalOwnerId(authentication);
        if (userId == null || properties.adminEmail() == null) {
            return false;
        }
        return appUserRepository.findById(userId)
                .map(user -> user.getEmail())
                .map(email -> email.equalsIgnoreCase(properties.adminEmail()))
                .orElse(false);
    }
}
