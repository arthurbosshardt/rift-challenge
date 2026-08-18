package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RankEmblemLoaderTest {

    private final RankEmblemLoader rankEmblemLoader = new RankEmblemLoader(new RemoteImageLoader());

    @Test
    void buildUrl_usesOpggMedalsPath() {
        assertThat(rankEmblemLoader.buildUrl("DIAMOND"))
                .isEqualTo("https://opgg-static.akamaized.net/images/medals_mini/diamond.png");
    }

    @Test
    void load_returnsEmptyForUnknownTier() {
        assertThat(rankEmblemLoader.load(null)).isEmpty();
        assertThat(rankEmblemLoader.load("")).isEmpty();
        assertThat(rankEmblemLoader.load("UNKNOWN")).isEmpty();
    }
}
