package com.riftchallenge.leaderboard.dto;

import java.util.List;

public record LeaderboardEntryResponse(
        String puuid,
        String gameName,
        String tagLine,
        String riotId,
        Integer profileIconId,
        String tier,
        String rankDivision,
        int leaguePoints,
        int wins,
        int losses,
        int gamesPlayed,
        double winRate,
        int winStreak,
        int lpGained,
        int position,
        /** Up to 10 most recent matches in the window, newest → oldest (strip order). */
        List<LeaderboardMatchHistoryResponse> recentMatches
) {
    public LeaderboardEntryResponse {
        if (recentMatches == null) {
            recentMatches = List.of();
        }
    }
}
