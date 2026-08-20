package com.riftchallenge.challenge;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SitemapController {

    private static final DateTimeFormatter LASTMOD = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ChallengeRepository challengeRepository;
    private final Clock clock;
    private final String frontendBaseUrl;

    public SitemapController(
            ChallengeRepository challengeRepository,
            Clock clock,
            @Value("${riftchallenge.frontend.base-url:https://rift-challenge.com}") String frontendBaseUrl
    ) {
        this.challengeRepository = challengeRepository;
        this.clock = clock;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    @GetMapping(value = "/api/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        Instant now = clock.instant();
        List<Challenge> publicChallenges =
                challengeRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(now);

        StringBuilder xml = new StringBuilder(2048);
        xml.append("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                  <url>
                    <loc>%s/</loc>
                    <changefreq>weekly</changefreq>
                    <priority>1.0</priority>
                  </url>
                  <url>
                    <loc>%s/home</loc>
                    <changefreq>weekly</changefreq>
                    <priority>0.9</priority>
                  </url>
                  <url>
                    <loc>%s/public-challenges</loc>
                    <changefreq>hourly</changefreq>
                    <priority>0.9</priority>
                  </url>
                """.formatted(frontendBaseUrl, frontendBaseUrl, frontendBaseUrl));

        for (Challenge challenge : publicChallenges) {
            String slug = ChallengeSharePaths.encodeSlug(challenge.getShareSlug());
            String lastmod = LASTMOD.format(challenge.getStartAt().atOffset(ZoneOffset.UTC));
            xml.append("  <url>\n")
                    .append("    <loc>")
                    .append(frontendBaseUrl)
                    .append("/challenges/")
                    .append(slug)
                    .append("</loc>\n")
                    .append("    <lastmod>")
                    .append(lastmod)
                    .append("</lastmod>\n")
                    .append("    <changefreq>daily</changefreq>\n")
                    .append("    <priority>0.8</priority>\n")
                    .append("  </url>\n");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://rift-challenge.com";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
