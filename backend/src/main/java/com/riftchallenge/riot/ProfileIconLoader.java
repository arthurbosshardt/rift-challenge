package com.riftchallenge.riot;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ProfileIconLoader {

    private final RemoteImageLoader remoteImageLoader;

    public ProfileIconLoader(RemoteImageLoader remoteImageLoader) {
        this.remoteImageLoader = remoteImageLoader;
    }

    public String buildUrl(int profileIconId) {
        return "https://ddragon.leagueoflegends.com/cdn/%s/img/profileicon/%d.png"
                .formatted(DDragonVersions.CURRENT, profileIconId);
    }

    public Optional<BufferedImage> load(Integer profileIconId) {
        if (profileIconId == null || profileIconId <= 0) {
            return Optional.empty();
        }

        Optional<BufferedImage> image = remoteImageLoader.load(
                "profile:" + profileIconId,
                buildUrl(profileIconId)
        );
        if (image.isPresent()) {
            return image;
        }

        String fallbackUrl = "https://raw.communitydragon.net/latest/plugins/rcp-be-lol-game-data/global/default/v1/profile-icons/%d.jpg"
                .formatted(profileIconId);
        return remoteImageLoader.load("profile-fallback:" + profileIconId, fallbackUrl);
    }
}
