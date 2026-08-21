package com.riftchallenge.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class WinStreakCalculatorTest {

    @Test
    void emptyList_returnsZero() {
        assertThat(WinStreakCalculator.longestWinStreak(List.of())).isZero();
    }

    @Test
    void allWins_returnsFullLength() {
        assertThat(WinStreakCalculator.longestWinStreak(List.of(true, true, true))).isEqualTo(3);
    }

    @Test
    void allLosses_returnsZero() {
        assertThat(WinStreakCalculator.longestWinStreak(List.of(false, false, false))).isZero();
    }

    @Test
    void alternating_returnsOne() {
        assertThat(WinStreakCalculator.longestWinStreak(List.of(true, false, true, false, true))).isEqualTo(1);
    }

    @Test
    void multipleStreaks_returnsLongest() {
        // W W L W W W L W -> longest run of wins is 3
        assertThat(WinStreakCalculator.longestWinStreak(
                List.of(true, true, false, true, true, true, false, true)
        )).isEqualTo(3);
    }

    @Test
    void streakAtTheEnd_isCounted() {
        assertThat(WinStreakCalculator.longestWinStreak(
                List.of(false, true, false, true, true, true, true)
        )).isEqualTo(4);
    }
}
