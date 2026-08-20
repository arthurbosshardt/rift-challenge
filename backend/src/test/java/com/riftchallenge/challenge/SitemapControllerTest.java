package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SitemapControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");

    @Mock
    private ChallengeRepository challengeRepository;

    private SitemapController sitemapController;

    @BeforeEach
    void setUp() {
        sitemapController = new SitemapController(
                challengeRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "https://rift-challenge.com/"
        );
    }

    @Test
    void sitemap_usesConfiguredBaseUrlAndUuidShareSlugs() {
        Challenge challenge = Challenge.create(
                UUID.randomUUID(),
                "Public challenge",
                ChallengeType.SOLOQ,
                NOW.minusSeconds(3600),
                NOW.plusSeconds(86_400),
                true
        );
        when(challengeRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(NOW))
                .thenReturn(List.of(challenge));

        String xml = sitemapController.sitemap();

        assertThat(xml).contains("<loc>https://rift-challenge.com/</loc>");
        assertThat(xml).contains("<loc>https://rift-challenge.com/public-challenges</loc>");
        assertThat(xml).contains("<loc>https://rift-challenge.com/challenges/" + challenge.getId() + "</loc>");
        assertThat(xml).doesNotContain("Public challenge");
    }
}
