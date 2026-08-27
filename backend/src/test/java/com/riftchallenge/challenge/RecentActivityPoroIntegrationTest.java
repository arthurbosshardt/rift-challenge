package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import com.riftchallenge.challenge.dto.AccountRecentGamesResponse;
import com.riftchallenge.leaderboard.AccountMatchRepository;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DATABASE_URL", matches = ".+")
class RecentActivityPoroIntegrationTest {

    private static final UUID PORO_USER_ID = UUID.fromString("c224fac1-c9f8-42ca-a065-0ce405036f0b");
    private static final String PORO_PUUID =
            "GrZdNMGNIyZamfLwcPkdIS7pQNUMyzzx2POlhF4hBnIfu0EH7-kPyx0gGqENS2s9ruKGhe6nFh0KRg";

    @Autowired
    private RecentActivityService recentActivityService;

    @Autowired
    private AccountMatchRepository accountMatchRepository;

    @Autowired
    private LeaderboardProperties leaderboardProperties;

    @Test
    void poroSeasonRowsAreLoadedFromDatabase() {
        var rows = accountMatchRepository.findSeasonActivitySince(
                PORO_PUUID,
                leaderboardProperties.seasonStartAt()
        );

        assertThat(rows).isNotEmpty();
    }

    @Test
    void poroRecentActivityIncludesChampionStats() {
        List<AccountRecentGamesResponse> result = recentActivityService.listRecentGames(PORO_USER_ID);

        assertThat(result).hasSize(1);
        AccountRecentGamesResponse account = result.getFirst();
        assertThat(account.syncedGames()).isGreaterThan(0);
        assertThat(account.games()).isNotEmpty();
        assertThat(account.champions())
                .as("champion stats must be built from imported season rows")
                .isNotEmpty();
        assertThat(account.champions().getFirst().games()).isEqualTo(account.syncedGames());
    }
}
