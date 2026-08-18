package com.riftchallenge.riot;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class RankEmblemLoader {

    private static final Set<String> SUPPORTED_TIERS = Set.of(
            "IRON",
            "BRONZE",
            "SILVER",
            "GOLD",
            "PLATINUM",
            "EMERALD",
            "DIAMOND",
            "MASTER",
            "GRANDMASTER",
            "CHALLENGER"
    );

    private final RemoteImageLoader remoteImageLoader;

    public RankEmblemLoader(RemoteImageLoader remoteImageLoader) {
        this.remoteImageLoader = remoteImageLoader;
    }

    public String buildUrl(String tier) {
        return "https://opgg-static.akamaized.net/images/medals_mini/%s.png"
                .formatted(tier.toLowerCase(Locale.ROOT));
    }

    public Optional<BufferedImage> load(String tier) {
        if (tier == null || tier.isBlank()) {
            return Optional.empty();
        }

        String normalizedTier = tier.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TIERS.contains(normalizedTier)) {
            return Optional.empty();
        }

        return remoteImageLoader.load("rank:" + normalizedTier, buildUrl(normalizedTier));
    }
}
