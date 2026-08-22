package com.riftchallenge.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountMatchRepository.AccountMatchHistoryRow;
import com.riftchallenge.leaderboard.dto.LeaderboardEntryResponse;
import com.riftchallenge.leaderboard.dto.LeaderboardSnapshot;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeaderboardComputationServiceTest {

    private static final Instant SEASON_START = Instant.parse("2026-07-29T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");

    @Mock
    private UserRiotAccountRepository userRiotAccountRepository;

    @Mock
    private LeaderboardAccountMatchRepository matchRepository;

    @Mock
    private LeaderboardAccountRankRepository rankRepository;

    @Mock
    private ChampionIconUrlService championIconUrlService;

    private LeaderboardComputationService service;

    @BeforeEach
    void setUp() {
        service = new LeaderboardComputationService(
                userRiotAccountRepository,
                matchRepository,
                rankRepository,
                new LeaderboardProperties(SEASON_START, "admin@example.com"),
                championIconUrlService
        );
        org.mockito.Mockito.lenient().when(rankRepository.findByRiotPuuid(any())).thenReturn(Optional.empty());
        org.mockito.Mockito.lenient().when(championIconUrlService.buildApiPath(any())).thenReturn(null);
    }

    @Test
    void belowMinGamesThreshold_excludedFromWinRateOnly() {
        UserRiotAccount account = account("puuid-below", "Below", "EUW");
        when(userRiotAccountRepository.findAll()).thenReturn(List.of(account));
        boolean[] wins = new boolean[19];
        java.util.Arrays.fill(wins, true);
        when(matchRepository.findHistorySince(account.getRiotPuuid(), SEASON_START)).thenReturn(history(wins));

        LeaderboardSnapshot snapshot = service.compute(NOW);

        assertThat(snapshot.season().byWinRate()).isEmpty();
        assertThat(snapshot.season().byWinStreak()).hasSize(1);
        assertThat(snapshot.season().byWinStreak().get(0).winStreak()).isEqualTo(19);
        assertThat(snapshot.season().byLpGained()).hasSize(1);
    }

    @Test
    void accountWithEnoughGames_ranksByWinRate() {
        UserRiotAccount account = account("puuid-active", "Active", "EUW");
        when(userRiotAccountRepository.findAll()).thenReturn(List.of(account));
        when(matchRepository.findHistorySince(account.getRiotPuuid(), SEASON_START)).thenReturn(history(
                true, true, true, true, false, true, true, true, false, true,
                true, true, true, true, false, true, true, true, false, true
        ));

        LeaderboardSnapshot snapshot = service.compute(NOW);

        assertThat(snapshot.season().byWinRate()).hasSize(1);
        LeaderboardEntryResponse entry = snapshot.season().byWinRate().get(0);
        assertThat(entry.puuid()).isEqualTo("puuid-active");
        assertThat(entry.gamesPlayed()).isEqualTo(20);
        assertThat(entry.wins()).isEqualTo(16);
        assertThat(entry.position()).isEqualTo(1);
        assertThat(entry.recentMatches()).isNotEmpty();
    }

    @Test
    void accountWithNoMatches_isExcludedEntirely() {
        UserRiotAccount account = account("puuid-idle", "Idle", "EUW");
        when(userRiotAccountRepository.findAll()).thenReturn(List.of(account));
        when(matchRepository.findHistorySince(account.getRiotPuuid(), SEASON_START)).thenReturn(List.of());

        LeaderboardSnapshot snapshot = service.compute(NOW);

        assertThat(snapshot.season().byWinRate()).isEmpty();
        assertThat(snapshot.season().byWinStreak()).isEmpty();
        assertThat(snapshot.season().byLpGained()).isEmpty();
    }

    @Test
    void rollingSevenDayWindow_excludesOlderMatchesButSeasonKeepsThem() {
        UserRiotAccount account = account("puuid-rolling", "Rolling", "EUW");
        when(userRiotAccountRepository.findAll()).thenReturn(List.of(account));

        List<AccountMatchHistoryRow> allSeasonMatches = new ArrayList<>();
        allSeasonMatches.addAll(historyAt(NOW.minusSeconds(30L * 24 * 3600), true, 25));
        allSeasonMatches.addAll(historyAt(NOW.minusSeconds(2L * 24 * 3600), false, 20));
        when(matchRepository.findHistorySince(account.getRiotPuuid(), SEASON_START)).thenReturn(allSeasonMatches);

        LeaderboardSnapshot snapshot = service.compute(NOW);

        LeaderboardEntryResponse seasonEntry = snapshot.season().byWinStreak().get(0);

        assertThat(seasonEntry.gamesPlayed()).isEqualTo(45);
        assertThat(seasonEntry.winStreak()).isEqualTo(25);
        // Rolling window is all losses (streak 0) -> below the 2-win-streak floor, excluded entirely.
        assertThat(snapshot.last7Days().byWinStreak()).isEmpty();
    }

    @Test
    void winStreakBelowTwo_excludedFromWinStreakLeaderboard() {
        UserRiotAccount account = account("puuid-single-win", "SingleWin", "EUW");
        when(userRiotAccountRepository.findAll()).thenReturn(List.of(account));
        when(matchRepository.findHistorySince(account.getRiotPuuid(), SEASON_START))
                .thenReturn(history(false, true, false));

        LeaderboardSnapshot snapshot = service.compute(NOW);

        assertThat(snapshot.season().byWinStreak()).isEmpty();
    }

    @Test
    void rankList_reflectsStoredAccountRank() {
        UserRiotAccount account = account("puuid-rank", "Ranked", "EUW");
        when(userRiotAccountRepository.findAll()).thenReturn(List.of(account));
        when(matchRepository.findHistorySince(account.getRiotPuuid(), SEASON_START)).thenReturn(List.of());
        when(rankRepository.findByRiotPuuid(account.getRiotPuuid())).thenReturn(Optional.of(
                LeaderboardAccountRank.create(account.getRiotPuuid(), NOW, "DIAMOND", "II", 40)
        ));

        LeaderboardSnapshot snapshot = service.compute(NOW);

        assertThat(snapshot.season().byRank()).hasSize(1);
        LeaderboardEntryResponse entry = snapshot.season().byRank().get(0);
        assertThat(entry.tier()).isEqualTo("DIAMOND");
        assertThat(entry.rankDivision()).isEqualTo("II");
        assertThat(entry.leaguePoints()).isEqualTo(40);
        assertThat(snapshot.last7Days().byRank()).isEqualTo(snapshot.season().byRank());
    }

    @Test
    void rankList_last7Days_includesSeasonHistoryWhenNoRollingStats() {
        UserRiotAccount account = account("puuid-rank-rolling", "Ranked", "EUW");
        when(userRiotAccountRepository.findAll()).thenReturn(List.of(account));
        when(matchRepository.findHistorySince(account.getRiotPuuid(), SEASON_START))
                .thenReturn(historyAt(NOW.minusSeconds(30L * 24 * 3600), true, 5));
        when(rankRepository.findByRiotPuuid(account.getRiotPuuid())).thenReturn(Optional.of(
                LeaderboardAccountRank.create(account.getRiotPuuid(), NOW, "PLATINUM", "I", 20)
        ));

        LeaderboardSnapshot snapshot = service.compute(NOW);

        assertThat(snapshot.last7Days().byRank()).hasSize(1);
        assertThat(snapshot.last7Days().byRank().get(0).recentMatches()).isNotEmpty();
    }

    private static UserRiotAccount account(String puuid, String gameName, String tagLine) {
        return UserRiotAccount.create(UUID.randomUUID(), new RiotAccountDto(puuid, gameName, tagLine));
    }

    private static List<AccountMatchHistoryRow> history(boolean... wins) {
        Instant start = SEASON_START.plusSeconds(3600);
        List<AccountMatchHistoryRow> result = new ArrayList<>();
        for (int i = 0; i < wins.length; i++) {
            result.add(new TestHistory("match-" + i, wins[i], 1, start.plusSeconds(i * 3600L)));
        }
        return result;
    }

    private static List<AccountMatchHistoryRow> historyAt(Instant gameStart, boolean win, int count) {
        List<AccountMatchHistoryRow> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            result.add(new TestHistory("match-at-" + gameStart + "-" + i, win, 1, gameStart.plusSeconds(i)));
        }
        return result;
    }

    private record TestHistory(
            String getMatchId,
            boolean isWin,
            Integer getChampionId,
            Instant getGameStart
    ) implements AccountMatchHistoryRow {
    }
}
