package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RemoteImageLoaderIntegrationTest {

    private final ProfileIconLoader profileIconLoader = new ProfileIconLoader(new RemoteImageLoader());
    private final RankEmblemLoader rankEmblemLoader = new RankEmblemLoader(new RemoteImageLoader());

    @Test
    void load_fetchesProfileIconAndRankEmblemFromRemoteSources() {
        Optional<BufferedImage> profileIcon = profileIconLoader.load(4020);
        Optional<BufferedImage> rankEmblem = rankEmblemLoader.load("DIAMOND");

        assertThat(profileIcon).isPresent();
        assertThat(profileIcon.get().getWidth()).isGreaterThan(0);
        assertThat(rankEmblem).isPresent();
        assertThat(rankEmblem.get().getWidth()).isGreaterThan(0);
    }
}
