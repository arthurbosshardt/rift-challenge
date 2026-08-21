package com.riftchallenge.leaderboard;

import java.util.List;

public final class WinStreakCalculator {

    private WinStreakCalculator() {
    }

    /** Longest run of consecutive wins in the given outcomes (order does not matter). */
    public static int longestWinStreak(List<Boolean> outcomes) {
        int longest = 0;
        int current = 0;
        for (boolean win : outcomes) {
            current = win ? current + 1 : 0;
            longest = Math.max(longest, current);
        }
        return longest;
    }
}
