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

    @Test
    void estimateFromMatches_prefersDiamondEndForHighVolumeDiamondGrind() {
        java.util.List<Boolean> winsOldestFirst = new java.util.ArrayList<>();
        for (int i = 0; i < 55; i++) {
            winsOldestFirst.add(true);
        }
        for (int i = 0; i < 34; i++) {
            winsOldestFirst.add(false);
        }

        RankReplayService.MatchBasedRankEstimate estimate = RankReplayService.estimateFromMatches(winsOldestFirst).orElseThrow();

        assertThat(estimate.refresh().tier()).isEqualTo("DIAMOND");
        assertThat(estimate.refresh().tier()).isNotEqualTo("MASTER");
        assertThat(tierOrderAtMost(estimate.baseline().tier(), "DIAMOND")).isTrue();
    }

    @Test
    void estimateFromMatches_keepsEvenEmeraldGrindNearEmerald() {
        java.util.List<Boolean> winsOldestFirst = new java.util.ArrayList<>();
        for (int i = 0; i < 32; i++) {
            winsOldestFirst.add(true);
        }
        for (int i = 0; i < 33; i++) {
            winsOldestFirst.add(false);
        }

        RankReplayService.MatchBasedRankEstimate estimate = RankReplayService.estimateFromMatches(winsOldestFirst).orElseThrow();

        assertThat(estimate.refresh().tier()).isEqualTo("EMERALD");
        assertThat(estimate.refresh().tier()).isNotEqualTo("MASTER");
        assertThat(estimate.baseline().tier()).isEqualTo("EMERALD");
    }

    @Test
    void estimateFromMatches_handlesPascalLikeRecordNearEmerald() {
        java.util.List<Boolean> winsOldestFirst = new java.util.ArrayList<>();
        for (int i = 0; i < 35; i++) {
            winsOldestFirst.add(true);
        }
        for (int i = 0; i < 29; i++) {
            winsOldestFirst.add(false);
        }

        RankReplayService.MatchBasedRankEstimate estimate = RankReplayService.estimateFromMatches(winsOldestFirst).orElseThrow();

        assertThat(estimate.refresh().tier()).isEqualTo("EMERALD");
        assertThat(estimate.refresh().tier()).isNotEqualTo("MASTER");
        assertThat(tierOrderAtMost(estimate.refresh().tier(), "EMERALD")).isTrue();
    }

    @Test
    void estimateFromMatches_derivesBaselineFromChosenEndAnchor() {
        java.util.List<Boolean> winsOldestFirst = java.util.List.of(true, false, true, true, false);
        RankReplayService.MatchBasedRankEstimate estimate = RankReplayService.estimateFromMatches(winsOldestFirst).orElseThrow();
        RankReplayService.RankState expectedBaseline = RankReplayService.replayBackward(
                estimate.refresh(),
                winsOldestFirst.reversed()
        );

        assertThat(estimate.baseline()).isEqualTo(expectedBaseline);
    }

    private static boolean tierOrderAtMost(String tier, String maxTier) {
        java.util.Map<String, Integer> order = java.util.Map.ofEntries(
                java.util.Map.entry("IRON", 0),
                java.util.Map.entry("BRONZE", 1),
                java.util.Map.entry("SILVER", 2),
                java.util.Map.entry("GOLD", 3),
                java.util.Map.entry("PLATINUM", 4),
                java.util.Map.entry("EMERALD", 5),
                java.util.Map.entry("DIAMOND", 6),
                java.util.Map.entry("MASTER", 7)
        );
        return order.getOrDefault(tier, 99) <= order.getOrDefault(maxTier, 99);
    }
}
