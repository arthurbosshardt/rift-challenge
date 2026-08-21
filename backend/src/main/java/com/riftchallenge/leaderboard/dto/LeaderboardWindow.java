package com.riftchallenge.leaderboard.dto;

import java.util.List;

/** One time window (current season or rolling last-7-days) worth of top-10 category lists. */
public record LeaderboardWindow(
        List<LeaderboardEntryResponse> byRank,
        List<LeaderboardEntryResponse> byWinRate,
        List<LeaderboardEntryResponse> byLpGained,
        List<LeaderboardEntryResponse> byWinStreak,
        int winRateMinGames
) {
}
