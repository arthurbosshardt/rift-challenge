package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
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

    @Test
    void create_usesStableUuidShareSlugWhenNameChanges() {
        Challenge challenge = Challenge.create(
                UUID.randomUUID(),
                "Original name",
                ChallengeType.SOLOQ,
                Instant.parse("2026-08-19T10:00:00Z"),
                Instant.parse("2026-08-20T10:00:00Z"),
                false
        );

        assertThat(challenge.getShareSlug()).isEqualTo(challenge.getId().toString());
        challenge.updateName("Renamed challenge");
        assertThat(challenge.getShareSlug()).isEqualTo(challenge.getId().toString());
        assertThat(ChallengeSharePaths.buildSharePath(challenge.getShareSlug()))
                .isEqualTo("/challenges/" + challenge.getId());
    }
}
