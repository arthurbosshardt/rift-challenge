package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchLpEstimatorTest {

    @Test
    void estimate_positiveWinRate_returnsPositiveLp() {
        int lp = MatchLpEstimator.estimateLpGainedFromMatches(6, 4, "GOLD");
        assertThat(lp).isEqualTo(40);
    }

    @Test
    void estimate_evenChallengerRecord_returnsZero() {
        int lp = MatchLpEstimator.estimateLpGainedFromMatches(5, 5, "CHALLENGER");
        assertThat(lp).isZero();
    }

    @Test
    void estimate_negativeWinRate_returnsNegativeLp() {
        int lp = MatchLpEstimator.estimateLpGainedFromMatches(4, 6, "CHALLENGER");
        assertThat(lp).isEqualTo(-24);
    }
}
