package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RankReplayServiceTest {

    @Test
    void replayBackward_reversesSingleWin() {
        RankReplayService.RankState start = new RankReplayService.RankState("GOLD", "II", 50);

        RankReplayService.RankState baseline = RankReplayService.replayBackward(start, java.util.List.of(true));

        assertThat(RankScoreConverter.toScore(baseline.tier(), baseline.rankDivision(), baseline.leaguePoints()))
                .isEqualTo(RankScoreConverter.toScore("GOLD", "II", 50) - MatchLpEstimator.averageWinLp("GOLD"));
    }

    @Test
    void replayBackward_reversesWinLossSequence() {
        RankReplayService.RankState end = new RankReplayService.RankState("PLATINUM", "IV", 0);

        RankReplayService.RankState baseline = RankReplayService.replayBackward(
                end,
                java.util.List.of(false, true)
        );

        int endScore = RankScoreConverter.toScore("PLATINUM", "IV", 0);
        int expectedScore = endScore
                + MatchLpEstimator.averageLossLp("PLATINUM")
                - MatchLpEstimator.averageWinLp("PLATINUM");

        assertThat(RankScoreConverter.toScore(baseline.tier(), baseline.rankDivision(), baseline.leaguePoints()))
                .isEqualTo(expectedScore);
    }
}
