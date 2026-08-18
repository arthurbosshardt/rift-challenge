package com.riftchallenge.riot;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class RemoteImageLoader {

    private static final String USER_AGENT = "RiftChallenge/1.0 (+https://rift-challenge.com)";

    private final Map<String, Optional<BufferedImage>> cache = new ConcurrentHashMap<>();

    public Optional<BufferedImage> load(String cacheKey, String url) {
        return cache.computeIfAbsent(cacheKey, ignored -> fetch(url));
    }

    private Optional<BufferedImage> fetch(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "image/*");
            connection.connect();

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }

            try (InputStream inputStream = connection.getInputStream()) {
                BufferedImage image = ImageIO.read(inputStream);
                return Optional.ofNullable(normalize(image));
            }
        } catch (IOException | RuntimeException ignored) {
            return Optional.empty();
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private BufferedImage normalize(BufferedImage source) {
        if (source == null) {
            return null;
        }

        BufferedImage normalized = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_ARGB
        );
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return normalized;
    }
}
