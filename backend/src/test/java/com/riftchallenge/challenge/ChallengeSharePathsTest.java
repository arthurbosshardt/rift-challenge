package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChallengeSharePathsTest {

    @Test
    void buildSharePath_encodesSpacesInChallengeName() {
        assertThat(ChallengeSharePaths.buildSharePath("Les solo petits soldats 2025"))
                .isEqualTo("/challenges/Les%20solo%20petits%20soldats%202025");
    }

    @Test
    void decodeSlug_reversesEncoding() {
        String slug = "Les solo petits soldats 2025";
        assertThat(ChallengeSharePaths.decodeSlug(ChallengeSharePaths.encodeSlug(slug))).isEqualTo(slug);
    }
}
