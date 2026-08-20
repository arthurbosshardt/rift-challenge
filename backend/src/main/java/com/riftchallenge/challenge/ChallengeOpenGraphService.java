package com.riftchallenge.challenge;

import com.riftchallenge.challenge.ChallengeOpenGraphImageRenderer.ChallengeOpenGraphPreview;
import com.riftchallenge.challenge.ChallengeOpenGraphImageRenderer.PodiumEntry;
import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import com.riftchallenge.challenge.dto.DuoProgressResponse;
import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.riot.ProfileIconLoader;
import com.riftchallenge.riot.RankEmblemLoader;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChallengeOpenGraphService {

    private static final ZoneId DISPLAY_ZONE = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.FRENCH)
            .withZone(DISPLAY_ZONE);

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeDuoRepository duoRepository;
    private final ChallengeProgressService progressService;
    private final ChallengeDuoProgressService duoProgressService;
    private final ProfileIconLoader profileIconLoader;
    private final RankEmblemLoader rankEmblemLoader;
    private final Clock clock;
    private final String frontendBaseUrl;

    public ChallengeOpenGraphService(
            ChallengeRepository challengeRepository,
            ChallengeParticipantRepository participantRepository,
            ChallengeDuoRepository duoRepository,
            ChallengeProgressService progressService,
            ChallengeDuoProgressService duoProgressService,
            ProfileIconLoader profileIconLoader,
            RankEmblemLoader rankEmblemLoader,
            Clock clock,
            @Value("${riftchallenge.frontend.base-url:https://rift-challenge.com}") String frontendBaseUrl
    ) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.duoRepository = duoRepository;
        this.progressService = progressService;
        this.duoProgressService = duoProgressService;
        this.profileIconLoader = profileIconLoader;
        this.rankEmblemLoader = rankEmblemLoader;
        this.clock = clock;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    public String renderPreviewHtml(String shareSlug) {
        Challenge challenge = findChallenge(shareSlug);
        PreviewContext context = buildPreviewContext(challenge);

        return """
                <!doctype html>
                <html lang="fr">
                <head>
                  <meta charset="utf-8" />
                  <title>%s</title>
                  <meta name="description" content="%s" />
                  <meta name="robots" content="index,follow,max-image-preview:large" />
                  <meta property="og:type" content="website" />
                  <meta property="og:site_name" content="Rift Challenge" />
                  <meta property="og:locale" content="fr_FR" />
                  <meta property="og:title" content="%s" />
                  <meta property="og:description" content="%s" />
                  <meta property="og:url" content="%s" />
                  <meta property="og:image" content="%s" />
                  <meta property="og:image:width" content="1200" />
                  <meta property="og:image:height" content="630" />
                  <meta name="twitter:card" content="summary_large_image" />
                  <meta name="twitter:title" content="%s" />
                  <meta name="twitter:description" content="%s" />
                  <meta name="twitter:image" content="%s" />
                  <link rel="canonical" href="%s" />
                  <meta http-equiv="refresh" content="0;url=%s" />
                </head>
                <body>
                  <h1>%s</h1>
                  <p>%s</p>
                  <p><a href="%s">%s</a></p>
                </body>
                </html>
                """.formatted(
                escapeHtml(context.title()),
                escapeHtml(context.description()),
                escapeHtml(challenge.getName()),
                escapeHtml(context.description()),
                escapeHtml(context.pageUrl()),
                escapeHtml(context.imageUrl()),
                escapeHtml(challenge.getName()),
                escapeHtml(context.description()),
                escapeHtml(context.imageUrl()),
                escapeHtml(context.pageUrl()),
                escapeHtml(context.pageUrl()),
                escapeHtml(challenge.getName()),
                escapeHtml(context.description()),
                escapeHtml(context.pageUrl()),
                escapeHtml(challenge.getName())
        );
    }

    public byte[] renderPreviewImage(String shareSlug) {
        Challenge challenge = findChallenge(shareSlug);
        PreviewContext context = buildPreviewContext(challenge);
        return ChallengeOpenGraphImageRenderer.renderPng(context.imagePreview());
    }

    private Challenge findChallenge(String shareSlug) {
        Challenge challenge = challengeRepository.findByShareSlug(shareSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        if (!challenge.isPublic()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found");
        }
        return challenge;
    }

    private PreviewContext buildPreviewContext(Challenge challenge) {
        Instant now = clock.instant();
        String status = ChallengeSummaryResponse.resolveStatus(
                challenge.getStartAt(),
                challenge.getEndAt(),
                now
        );
        int entryCount = challenge.getType() == ChallengeType.DUOQ
                ? (int) duoRepository.countByChallengeId(challenge.getId())
                : (int) participantRepository.countByChallengeId(challenge.getId());

        List<PodiumEntry> podium = buildPodium(challenge, status);
        String title = challenge.getName() + " — Rift Challenge";
        String description = buildDescription(challenge, status, entryCount, podium);
        String pageUrl = frontendBaseUrl + ChallengeSharePaths.buildSharePath(challenge.getShareSlug());
        String imageUrl = frontendBaseUrl
                + "/api/challenge-preview-image?slug="
                + ChallengeSharePaths.encodeSlug(challenge.getShareSlug());
        String subtitle = buildImageSubtitle(challenge, status, entryCount);
        ChallengeOpenGraphPreview imagePreview = new ChallengeOpenGraphPreview(challenge.getName(), subtitle, podium);

        return new PreviewContext(title, description, pageUrl, imageUrl, imagePreview);
    }

    private List<PodiumEntry> buildPodium(Challenge challenge, String status) {
        if ("NOT_STARTED".equals(status)) {
            return List.of();
        }

        if (challenge.getType() == ChallengeType.DUOQ) {
            return duoProgressService.buildPreview(challenge).stream()
                    .limit(3)
                    .map(this::toDuoPodiumEntry)
                    .toList();
        }

        return progressService.buildPreviewProgress(
                        challenge,
                        participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId())
                ).stream()
                .limit(3)
                .map(this::toSoloPodiumEntry)
                .toList();
    }

    private PodiumEntry toSoloPodiumEntry(ParticipantProgressResponse participant) {
        return new PodiumEntry(
                participant.position(),
                participant.gameName(),
                formatParticipantDetail(participant),
                loadIcons(participant.profileIconId()),
                loadRankEmblem(participant.currentTier())
        );
    }

    private PodiumEntry toDuoPodiumEntry(DuoProgressResponse duo) {
        return new PodiumEntry(
                duo.position(),
                duo.player1().gameName() + " · " + duo.player2().gameName(),
                formatDuoDetail(duo),
                loadIcons(duo.player1().profileIconId(), duo.player2().profileIconId()),
                loadRankEmblem(resolveDuoTier(duo))
        );
    }

    private BufferedImage loadRankEmblem(String tier) {
        return rankEmblemLoader.load(tier).orElse(null);
    }

    private static String resolveDuoTier(DuoProgressResponse duo) {
        String tier1 = duo.player1().currentTier();
        String tier2 = duo.player2().currentTier();
        if (tier1 != null && tier2 != null) {
            return duo.player1().rankScore() >= duo.player2().rankScore() ? tier1 : tier2;
        }
        return tier1 != null ? tier1 : tier2;
    }

    private List<BufferedImage> loadIcons(Integer... profileIconIds) {
        List<BufferedImage> icons = new ArrayList<>();
        for (Integer profileIconId : profileIconIds) {
            profileIconLoader.load(profileIconId).ifPresent(icons::add);
        }
        return icons;
    }

    private String buildDescription(
            Challenge challenge,
            String status,
            int entryCount,
            List<PodiumEntry> podium
    ) {
        String base = buildDescription(challenge, status, entryCount);
        if (podium.isEmpty()) {
            return base;
        }

        List<String> segments = new ArrayList<>();
        for (PodiumEntry entry : podium) {
            segments.add("#" + entry.position() + " " + entry.label());
        }
        return base + " · " + String.join(" · ", segments);
    }

    private String buildDescription(Challenge challenge, String status, int entryCount) {
        String typeLabel = challenge.getType() == ChallengeType.DUOQ ? "DuoQ" : "SoloQ";
        String dates = DATE_FORMAT.format(challenge.getStartAt()) + " → " + DATE_FORMAT.format(challenge.getEndAt());
        String entries = challenge.getType() == ChallengeType.DUOQ
                ? entryCount + (entryCount > 1 ? " duos" : " duo")
                : entryCount + (entryCount > 1 ? " joueurs" : " joueur");
        return typeLabel + " · " + dates + " · " + entries + " · " + statusLabel(status);
    }

    private String buildImageSubtitle(Challenge challenge, String status, int entryCount) {
        String typeLabel = challenge.getType() == ChallengeType.DUOQ ? "DuoQ" : "SoloQ";
        String entries = challenge.getType() == ChallengeType.DUOQ
                ? entryCount + (entryCount > 1 ? " duos" : " duo")
                : entryCount + (entryCount > 1 ? " joueurs" : " joueur");
        return typeLabel + " · " + statusLabel(status) + " · " + entries;
    }

    private String formatParticipantDetail(ParticipantProgressResponse participant) {
        if (participant.hasRankData()) {
            String rank = formatRank(participant.currentTier(), participant.currentRank(), participant.currentLp());
            if (participant.lpGained() == 0) {
                return rank;
            }
            String lpPrefix = participant.lpGained() > 0 ? "+" : "";
            return lpPrefix + participant.lpGained() + " LP · " + rank;
        }
        return participant.wins() + "V · " + participant.losses() + "D";
    }

    private String formatDuoDetail(DuoProgressResponse duo) {
        if (duo.combinedLpGained() == 0 && !duo.player1().hasRankData() && !duo.player2().hasRankData()) {
            return duo.wins() + "V · " + duo.losses() + "D";
        }
        if (duo.combinedLpGained() == 0) {
            return duo.wins() + "V · " + duo.losses() + "D";
        }
        String lpPrefix = duo.combinedLpGained() > 0 ? "+" : "";
        return lpPrefix + duo.combinedLpGained() + " LP · " + duo.wins() + "V · " + duo.losses() + "D";
    }

    private static String formatRank(String tier, String rank, int lp) {
        if (tier == null || tier.isBlank()) {
            return lp + " LP";
        }
        String tierLabel = tierLabelFr(tier);
        if (rank == null || rank.isBlank()) {
            return tierLabel + " · " + lp + " LP";
        }
        return tierLabel + " " + rank + " · " + lp + " LP";
    }

    private static String tierLabelFr(String tier) {
        return switch (tier.toUpperCase(Locale.ROOT)) {
            case "IRON" -> "Fer";
            case "BRONZE" -> "Bronze";
            case "SILVER" -> "Argent";
            case "GOLD" -> "Or";
            case "PLATINUM" -> "Platine";
            case "EMERALD" -> "Émeraude";
            case "DIAMOND" -> "Diamant";
            case "MASTER" -> "Master";
            case "GRANDMASTER" -> "Grandmaster";
            case "CHALLENGER" -> "Challenger";
            default -> tier;
        };
    }

    private static String statusLabel(String status) {
        return switch (status) {
            case "NOT_STARTED" -> "Pas encore commencé";
            case "FINISHED" -> "Terminé";
            default -> "En cours";
        };
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://rift-challenge.com";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record PreviewContext(
            String title,
            String description,
            String pageUrl,
            String imageUrl,
            ChallengeOpenGraphPreview imagePreview
    ) {
    }
}
