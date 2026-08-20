package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.riot.ProfileIconLoader;
import com.riftchallenge.riot.RankEmblemLoader;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ChallengeOpenGraphServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private ChallengeDuoRepository duoRepository;

    @Mock
    private ChallengeProgressService progressService;

    @Mock
    private ChallengeDuoProgressService duoProgressService;

    @Mock
    private ProfileIconLoader profileIconLoader;

    @Mock
    private RankEmblemLoader rankEmblemLoader;

    private ChallengeOpenGraphService openGraphService;

    @BeforeEach
    void setUp() {
        openGraphService = new ChallengeOpenGraphService(
                challengeRepository,
                participantRepository,
                duoRepository,
                progressService,
                duoProgressService,
                profileIconLoader,
                rankEmblemLoader,
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
                Instant.parse("2025-05-03T00:00:00Z"));

        when(challengeRepository.findByShareSlug(challenge.getShareSlug())).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challenge.getId())).thenReturn(8L);
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())).thenReturn(List.of());
        when(progressService.buildPreviewProgress(challenge, List.of())).thenReturn(List.of());

        String html = openGraphService.renderPreviewHtml(challenge.getShareSlug());

        assertThat(html).contains("property=\"og:title\" content=\"Les solo petits soldats 2025\"");
        assertThat(html).contains("SoloQ");
        assertThat(html).contains("8 joueurs");
        assertThat(html).contains("Terminé");
        assertThat(html).contains("https://rift-challenge.com/challenges/" + challenge.getId());
        assertThat(html).contains("property=\"og:image\" content=\"https://rift-challenge.com/api/challenge-preview-image?slug=");
        assertThat(html).contains("summary_large_image");
    }

    @Test
    void renderPreviewHtml_includesTopThreeInDescriptionAndDynamicImage() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId,
                "Race active",
                ChallengeType.SOLOQ,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"));
        ChallengeParticipant first = participant(challenge.getId(), "Nikos", "EUW");
        ChallengeParticipant second = participant(challenge.getId(), "Pascal", "EUW");
        ChallengeParticipant third = participant(challenge.getId(), "Jungle", "EUW");

        when(challengeRepository.findByShareSlug(challenge.getShareSlug())).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challenge.getId())).thenReturn(3L);
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId()))
                .thenReturn(List.of(first, second, third));
        when(progressService.buildPreviewProgress(challenge, List.of(first, second, third))).thenReturn(List.of(
                progress(first, 1, "Nikos", 42, "DIAMOND", "IV"),
                progress(second, 2, "Pascal", 28, "EMERALD", "IV"),
                progress(third, 3, "Jungle", 15, "EMERALD", "IV")
        ));

        String html = openGraphService.renderPreviewHtml(challenge.getShareSlug());

        assertThat(html).contains("#1 Nikos");
        assertThat(html).contains("#2 Pascal");
        assertThat(html).contains("#3 Jungle");
        assertThat(html).contains("/api/challenge-preview-image?slug=" + challenge.getId());
    }

    @Test
    void renderPreviewImage_returnsPngWithPodium() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId,
                "Race active",
                ChallengeType.SOLOQ,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"));
        ChallengeParticipant first = participant(challenge.getId(), "Nikos", "EUW");

        when(challengeRepository.findByShareSlug(challenge.getShareSlug())).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challenge.getId())).thenReturn(1L);
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId()))
                .thenReturn(List.of(first));
        when(progressService.buildPreviewProgress(challenge, List.of(first))).thenReturn(List.of(
                progress(first, 1, "Nikos", 42, "DIAMOND", "IV")
        ));

        byte[] png = openGraphService.renderPreviewImage(challenge.getShareSlug());

        assertThat(png).isNotEmpty();
        assertThat(png[0]).isEqualTo((byte) 0x89);
    }

    private static ChallengeParticipant participant(UUID challengeId, String gameName, String tagLine) {
        return ChallengeParticipant.create(
                challengeId,
                new RiotAccountDto("puuid-" + gameName, gameName, tagLine)
        );
    }

    private static ParticipantProgressResponse progress(
            ChallengeParticipant participant,
            int position,
            String gameName,
            int lpGained,
            String tier,
            String rank
    ) {
        return ParticipantProgressResponse.withRankData(
                participant,
                position,
                tier,
                rank,
                12,
                lpGained,
                1000 + lpGained,
                5,
                3,
                false
        );
    }

    @Test
    void renderPreviewHtml_whenMaxGamesMode_avoidsNullEndDateAndUsesGamesLabel() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId,
                "Max games race",
                ChallengeType.SOLOQ,
                Instant.parse("2026-08-01T00:00:00Z"),
                null,
                20);
        ChallengeParticipant first = participant(challenge.getId(), "Nikos", "EUW");

        when(challengeRepository.findByShareSlug(challenge.getShareSlug())).thenReturn(Optional.of(challenge));
        when(participantRepository.countByChallengeId(challenge.getId())).thenReturn(1L);
        when(participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId()))
                .thenReturn(List.of(first));
        when(progressService.buildPreviewProgress(challenge, List.of(first))).thenReturn(List.of(
                progress(first, 1, "Nikos", 12, "GOLD", "II")
        ));

        String html = openGraphService.renderPreviewHtml(challenge.getShareSlug());

        assertThat(html).contains("20 games");
        assertThat(html).contains("En cours");
        assertThat(html).doesNotContain("null");
    }

    @Test
    void renderPreviewHtml_rendersForAnyChallenge() {
        UUID ownerId = UUID.randomUUID();
        Challenge challenge = Challenge.create(
                ownerId,
                "Any race",
                ChallengeType.SOLOQ,
                NOW.plusSeconds(3600));
        when(challengeRepository.findByShareSlug(challenge.getShareSlug())).thenReturn(Optional.of(challenge));

        String html = openGraphService.renderPreviewHtml(challenge.getShareSlug());

        assertThat(html).contains("Any race");
    }

    @Test
    void renderPreviewHtml_whenChallengeMissing_throwsNotFound() {
        when(challengeRepository.findByShareSlug("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> openGraphService.renderPreviewHtml("missing"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }
}
