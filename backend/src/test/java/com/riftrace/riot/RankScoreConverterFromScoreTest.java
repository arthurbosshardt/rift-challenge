package com.riftrace.riot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RankScoreConverterFromScoreTest {

    @Test
    void fromScore_roundTripsGoldTwo() {
        int score = RankScoreConverter.toScore("GOLD", "II", 50);

        RankScoreConverter.RankComponents components = RankScoreConverter.fromScore(score);

        assertThat(components.tier()).isEqualTo("GOLD");
        assertThat(components.rankDivision()).isEqualTo("II");
        assertThat(components.leaguePoints()).isEqualTo(50);
    }

    @Test
    void fromScore_handlesMasterTier() {
        int score = RankScoreConverter.toScore("MASTER", null, 125);

        RankScoreConverter.RankComponents components = RankScoreConverter.fromScore(score);

        assertThat(components.tier()).isEqualTo("MASTER");
        assertThat(components.rankDivision()).isNull();
        assertThat(components.leaguePoints()).isEqualTo(125);
    }
}
