package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChallengeSharePathsTest {

    @Test
    void encodeSlug_keepsUuidUnchanged() {
        String slug = UUID.randomUUID().toString();
        assertThat(ChallengeSharePaths.encodeSlug(slug)).isEqualTo(slug);
        assertThat(ChallengeSharePaths.buildSharePath(slug)).isEqualTo("/challenges/" + slug);
    }

    @Test
    void encodeSlug_encodesUnsafeCharacters() {
        assertThat(ChallengeSharePaths.encodeSlug("a b/c"))
                .isEqualTo("a%20b%2Fc");
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
