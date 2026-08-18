package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChallengeOpenGraphServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private ChallengeDuoRepository duoRepository;

    private ChallengeOpenGraphService openGraphService;

    @BeforeEach
    void setUp() {
        openGraphService = new ChallengeOpenGraphService(
                challengeRepository,
                participantRepository,
                duoRepository,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "https://rift-challenge.com"
        );
    }

    @Test
    void renderPreviewHtml_includesChallengeSummaryForCrawlers() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId,
                "Les solo petits soldats 2025",
                ChallengeType.SOLOQ,
                Instant.parse("2025-02-17T00:00:00Z"),
                Instant.parse("2025-05-03T00:00:00Z"),
                true
        );

        when(challengeRepository.findByShareSlug("Les solo petits soldats 2025")).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challenge.getId())).thenReturn(8L);

        String html = openGraphService.renderPreviewHtml("Les solo petits soldats 2025");

        assertThat(html).contains("property=\"og:title\" content=\"Les solo petits soldats 2025\"");
        assertThat(html).contains("SoloQ");
        assertThat(html).contains("8 joueurs");
        assertThat(html).contains("Terminé");
        assertThat(html).contains("https://rift-challenge.com/challenges/Les%20solo%20petits%20soldats%202025");
    }
}
