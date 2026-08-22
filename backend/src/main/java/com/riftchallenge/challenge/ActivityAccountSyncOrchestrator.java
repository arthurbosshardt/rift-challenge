package com.riftchallenge.challenge;

import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountMatchRepository;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import com.riftchallenge.riot.RiotLeagueClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ActivityAccountSyncOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ActivityAccountSyncOrchestrator.class);

    private final UserRiotAccountRepository userRiotAccountRepository;
    private final LeaderboardAccountMatchRepository accountMatchRepository;
    private final ActivityAccountBackgroundSyncService backgroundSyncService;
    private final RiotLeagueClient riotLeagueClient;
    private final LeaderboardProperties leaderboardProperties;

    public ActivityAccountSyncOrchestrator(
            UserRiotAccountRepository userRiotAccountRepository,
            LeaderboardAccountMatchRepository accountMatchRepository,
            ActivityAccountBackgroundSyncService backgroundSyncService,
            RiotLeagueClient riotLeagueClient,
            LeaderboardProperties leaderboardProperties
    ) {
        this.userRiotAccountRepository = userRiotAccountRepository;
        this.accountMatchRepository = accountMatchRepository;
        this.backgroundSyncService = backgroundSyncService;
        this.riotLeagueClient = riotLeagueClient;
        this.leaderboardProperties = leaderboardProperties;
    }

    public void scheduleSyncForAccount(UserRiotAccount account) {
        int seasonMatchTotal = ActivitySeasonMatchTotals.resolve(riotLeagueClient, account.getRiotPuuid());
        int storedMatchCount = (int) accountMatchRepository.countSeasonMatchesSince(
                account.getRiotPuuid(),
                leaderboardProperties.seasonStartAt()
        );
        backgroundSyncService.scheduleSyncIfIdle(account, seasonMatchTotal, storedMatchCount);
    }

    public void scheduleSyncForAllLinkedAccounts() {
        for (UserRiotAccount account : userRiotAccountRepository.findAll()) {
            try {
                scheduleSyncForAccount(account);
            } catch (RuntimeException exception) {
                log.warn("Unable to schedule activity sync for linked account {}: {}", account.getId(), exception.getMessage());
            }
        }
    }
}
