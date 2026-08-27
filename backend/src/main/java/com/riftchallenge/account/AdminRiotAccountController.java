package com.riftchallenge.account;

import com.riftchallenge.account.AppUser;
import com.riftchallenge.account.dto.BulkRegisterRiotAccountsRequest;
import com.riftchallenge.account.dto.BulkRegisterRiotAccountsResponse;
import com.riftchallenge.authentication.AuthenticatedUserIds;
import com.riftchallenge.leaderboard.LeaderboardAccountSyncService;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import com.riftchallenge.leaderboard.RiotHistoryBackfillService;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final RiotHistoryBackfillService riotHistoryBackfillService;
    private final ExecutorService riotBackfillOrchestratorExecutor;
    private final LeaderboardProperties properties;
    private final Clock clock;

    public AdminRiotAccountController(
            RiotAccountService riotAccountService,
            AppUserRepository appUserRepository,
            LeaderboardAccountSyncService leaderboardAccountSyncService,
            RiotHistoryBackfillService riotHistoryBackfillService,
            ExecutorService riotBackfillOrchestratorExecutor,
            LeaderboardProperties properties,
            Clock clock
    ) {
        this.riotAccountService = riotAccountService;
        this.appUserRepository = appUserRepository;
        this.leaderboardAccountSyncService = leaderboardAccountSyncService;
        this.riotHistoryBackfillService = riotHistoryBackfillService;
        this.riotBackfillOrchestratorExecutor = riotBackfillOrchestratorExecutor;
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

    /**
     * Kicks off a one-off backfill of every account's full ranked-solo match history in the
     * background and returns immediately; the run itself can take a long time even with a
     * production key's higher quota. Progress/outcome is only observable in the server logs.
     */
    @PostMapping("/backfill-history")
    public ResponseEntity<Void> backfillHistory(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed to backfill Riot accounts");
        }
        riotBackfillOrchestratorExecutor.submit(riotHistoryBackfillService::backfillAllAccounts);
        return ResponseEntity.accepted().build();
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
