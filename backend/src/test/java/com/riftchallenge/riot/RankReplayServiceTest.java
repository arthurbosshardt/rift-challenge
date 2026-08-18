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
    void replayForward_appliesWinLossSequence() {
        RankReplayService.RankState start = new RankReplayService.RankState("IRON", "IV", 0);

        RankReplayService.RankState end = RankReplayService.replayForward(
                start,
                java.util.List.of(true, false)
        );

        int startScore = RankScoreConverter.toScore("IRON", "IV", 0);
        int expectedScore = startScore
                + MatchLpEstimator.averageWinLp("IRON")
                - MatchLpEstimator.averageLossLp("IRON");

        assertThat(RankScoreConverter.toScore(end.tier(), end.rankDivision(), end.leaguePoints()))
                .isEqualTo(expectedScore);
    }

    @Test
    void replayForwardAndBackward_areInverses() {
        RankReplayService.RankState original = new RankReplayService.RankState("GOLD", "III", 40);
        java.util.List<Boolean> winsOldestFirst = java.util.List.of(true, true, false, true, false);

        RankReplayService.RankState end = RankReplayService.replayForward(original, winsOldestFirst);
        RankReplayService.RankState baseline = RankReplayService.replayBackward(
                end,
                winsOldestFirst.reversed()
        );

        assertThat(RankScoreConverter.toScore(baseline.tier(), baseline.rankDivision(), baseline.leaguePoints()))
                .isEqualTo(RankScoreConverter.toScore(original.tier(), original.rankDivision(), original.leaguePoints()));
    }
}
