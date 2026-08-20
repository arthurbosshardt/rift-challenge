package com.riftchallenge.challenge;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SitemapController {

    private static final DateTimeFormatter LASTMOD = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final ChallengeRepository challengeRepository;
    private final Clock clock;

    public SitemapController(ChallengeRepository challengeRepository, Clock clock) {
        this.challengeRepository = challengeRepository;
        this.clock = clock;
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
                    <loc>https://rift-challenge.com/</loc>
                    <changefreq>weekly</changefreq>
                    <priority>1.0</priority>
                  </url>
                  <url>
                    <loc>https://rift-challenge.com/home</loc>
                    <changefreq>weekly</changefreq>
                    <priority>0.9</priority>
                  </url>
                  <url>
                    <loc>https://rift-challenge.com/public-challenges</loc>
                    <changefreq>hourly</changefreq>
                    <priority>0.9</priority>
                  </url>
                """);

        for (Challenge challenge : publicChallenges) {
            String slug = ChallengeSharePaths.encodeSlug(challenge.getShareSlug());
            String lastmod = LASTMOD.format(challenge.getStartAt().atOffset(ZoneOffset.UTC));
            xml.append("  <url>\n")
                    .append("    <loc>https://rift-challenge.com/challenges/")
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
}
