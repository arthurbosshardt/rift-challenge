package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RankScoreConverterTest {

    @Test
    void lpGained_sameRank_isZero() {
        int gained = RankScoreConverter.lpGained("GOLD", "II", 50, "GOLD", "II", 50);
        assertThat(gained).isZero();
    }

    @Test
    void lpGained_rankUp_isPositive() {
        int gained = RankScoreConverter.lpGained("GOLD", "II", 50, "GOLD", "I", 20);
        assertThat(gained).isEqualTo(70);
    }
}
