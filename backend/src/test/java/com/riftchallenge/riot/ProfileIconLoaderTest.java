package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProfileIconLoaderTest {

    private final ProfileIconLoader profileIconLoader = new ProfileIconLoader(new RemoteImageLoader());

    @Test
    void buildUrl_usesDataDragonProfileIconPath() {
        assertThat(profileIconLoader.buildUrl(4568))
                .isEqualTo("https://ddragon.leagueoflegends.com/cdn/16.16.1/img/profileicon/4568.png");
    }

    @Test
    void load_returnsEmptyForMissingId() {
        assertThat(profileIconLoader.load(null)).isEmpty();
        assertThat(profileIconLoader.load(0)).isEmpty();
    }
}
