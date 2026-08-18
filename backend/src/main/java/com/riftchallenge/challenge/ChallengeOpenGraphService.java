package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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
    private final Clock clock;
    private final String frontendBaseUrl;

    public ChallengeOpenGraphService(
            ChallengeRepository challengeRepository,
            ChallengeParticipantRepository participantRepository,
            ChallengeDuoRepository duoRepository,
            Clock clock,
            @Value("${riftchallenge.frontend.base-url:https://rift-challenge.com}") String frontendBaseUrl
    ) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.duoRepository = duoRepository;
        this.clock = clock;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    public String renderPreviewHtml(String shareSlug) {
        Challenge challenge = challengeRepository.findByShareSlug(shareSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        Instant now = clock.instant();
        String status = ChallengeSummaryResponse.resolveStatus(
                challenge.getStartAt(),
                challenge.getEndAt(),
                now
        );
        int entryCount = challenge.getType() == ChallengeType.DUOQ
                ? (int) duoRepository.countByChallengeId(challenge.getId())
                : (int) participantRepository.countByChallengeId(challenge.getId());

        String title = challenge.getName() + " — Rift Challenge";
        String description = buildDescription(challenge, status, entryCount);
        String pageUrl = frontendBaseUrl + ChallengeSharePaths.buildSharePath(challenge.getShareSlug());
        String imageUrl = frontendBaseUrl + "/logo.png?v=20260818";

        return """
                <!doctype html>
                <html lang="fr">
                <head>
                  <meta charset="utf-8" />
                  <title>%s</title>
                  <meta name="description" content="%s" />
                  <meta property="og:type" content="website" />
                  <meta property="og:site_name" content="Rift Challenge" />
                  <meta property="og:title" content="%s" />
                  <meta property="og:description" content="%s" />
                  <meta property="og:url" content="%s" />
                  <meta property="og:image" content="%s" />
                  <meta name="twitter:card" content="summary" />
                  <meta name="twitter:title" content="%s" />
                  <meta name="twitter:description" content="%s" />
                  <meta name="twitter:image" content="%s" />
                  <link rel="canonical" href="%s" />
                  <meta http-equiv="refresh" content="0;url=%s" />
                </head>
                <body>
                  <p><a href="%s">%s</a></p>
                </body>
                </html>
                """.formatted(
                escapeHtml(title),
                escapeHtml(description),
                escapeHtml(challenge.getName()),
                escapeHtml(description),
                escapeHtml(pageUrl),
                escapeHtml(imageUrl),
                escapeHtml(challenge.getName()),
                escapeHtml(description),
                escapeHtml(imageUrl),
                escapeHtml(pageUrl),
                escapeHtml(pageUrl),
                escapeHtml(pageUrl),
                escapeHtml(challenge.getName())
        );
    }

    private String buildDescription(Challenge challenge, String status, int entryCount) {
        String typeLabel = challenge.getType() == ChallengeType.DUOQ ? "DuoQ" : "SoloQ";
        String dates = DATE_FORMAT.format(challenge.getStartAt()) + " → " + DATE_FORMAT.format(challenge.getEndAt());
        String entries = challenge.getType() == ChallengeType.DUOQ
                ? entryCount + (entryCount > 1 ? " duos" : " duo")
                : entryCount + (entryCount > 1 ? " joueurs" : " joueur");
        return typeLabel + " · " + dates + " · " + entries + " · " + statusLabel(status);
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
}
