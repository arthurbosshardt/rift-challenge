package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RiotMatchDurationsTest {

    @Test
    void normalizeSeconds_modernMatch_keepsSeconds() {
        assertThat(RiotMatchDurations.normalizeSeconds(1_842L)).isEqualTo(1_842L);
    }

    @Test
    void normalizeSeconds_legacyMatch_convertsMilliseconds() {
        assertThat(RiotMatchDurations.normalizeSeconds(1_842_000L)).isEqualTo(1_842L);
    }
}
