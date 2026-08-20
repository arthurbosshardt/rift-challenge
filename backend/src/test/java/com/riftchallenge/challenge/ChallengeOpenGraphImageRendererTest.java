package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ChallengeOpenGraphImageRendererTest {

    @Test
    void brandAssets_areAvailableOnClasspath() {
        assertThat(ChallengeOpenGraphImageRenderer.class.getResource("/brand/logo.png")).isNotNull();
        assertThat(ChallengeOpenGraphImageRenderer.class.getResource("/brand/LemonMilk-Regular.ttf")).isNotNull();
    }

    @Test
    void renderPng_producesValidPngBytes() {
        byte[] png = ChallengeOpenGraphImageRenderer.renderPng(
                new ChallengeOpenGraphImageRenderer.ChallengeOpenGraphPreview(
                        "Les solo petits soldats 2025",
                        "SoloQ · En cours · 8 joueurs",
                        List.of(
                                new ChallengeOpenGraphImageRenderer.PodiumEntry(
                                        1,
                                        "Nikos",
                                        "+42 LP · Diamant IV · 12 LP",
                                        List.of(),
                                        null
                                ),
                                new ChallengeOpenGraphImageRenderer.PodiumEntry(
                                        2,
                                        "Pascal",
                                        "+28 LP · Émeraude IV · 45 LP",
                                        List.of(),
                                        null
                                ),
                                new ChallengeOpenGraphImageRenderer.PodiumEntry(
                                        3,
                                        "Jungle",
                                        "+15 LP · Émeraude IV · 10 LP",
                                        List.of(),
                                        null
                                )
                        )
                )
        );

        assertThat(png).isNotEmpty();
        assertThat(png[0]).isEqualTo((byte) 0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }
}
