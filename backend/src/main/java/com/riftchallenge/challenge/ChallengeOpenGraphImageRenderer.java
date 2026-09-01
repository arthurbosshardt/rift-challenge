package com.riftchallenge.challenge;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ChallengeOpenGraphImageRenderer {

    private static final Logger log = LoggerFactory.getLogger(ChallengeOpenGraphImageRenderer.class);

    static final int WIDTH = 1200;
    static final int HEIGHT = 630;

    private static final Color BACKGROUND = new Color(18, 23, 29);
    private static final Color TITLE_COLOR = new Color(212, 176, 106);
    private static final Color SUBTITLE_COLOR = new Color(168, 176, 191);
    private static final Color NAME_COLOR = new Color(232, 236, 241);
    private static final Color META_COLOR = new Color(140, 148, 163);
    private static final Color PLACEHOLDER_FILL = new Color(36, 44, 56);
    private static final Color BRAND_COLOR = new Color(197, 160, 89, 220);

    private static final Font BRAND_FONT = loadBrandFont();
    private static final BufferedImage BRAND_LOGO = loadBrandLogo();

    private ChallengeOpenGraphImageRenderer() {
    }

    public static byte[] renderPng(ChallengeOpenGraphPreview preview) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setColor(BACKGROUND);
            graphics.fillRect(0, 0, WIDTH, HEIGHT);

            drawHeader(graphics, preview.challengeName(), preview.subtitle());

            int rowStartY = 210;
            int rowHeight = 118;
            List<PodiumEntry> podium = preview.podium();
            for (int index = 0; index < podium.size(); index++) {
                drawPodiumRow(graphics, podium.get(index), 72, rowStartY + index * rowHeight, WIDTH - 144, rowHeight - 14);
            }

            drawBrandMark(graphics);
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void drawHeader(Graphics2D graphics, String title, String subtitle) {
        Font titleFont = brandFont(52f);
        Font subtitleFont = uiFont(Font.PLAIN, 28);

        graphics.setFont(titleFont);
        graphics.setColor(TITLE_COLOR);
        drawTruncatedText(graphics, title, 72, 108, WIDTH - 144);

        graphics.setFont(subtitleFont);
        graphics.setColor(SUBTITLE_COLOR);
        drawTruncatedText(graphics, subtitle, 72, 156, WIDTH - 144);
    }

    private static void drawPodiumRow(
            Graphics2D graphics,
            PodiumEntry entry,
            int x,
            int y,
            int width,
            int height
    ) {
        PodiumPalette palette = paletteFor(entry.position());
        int corner = 18;

        graphics.setColor(palette.background());
        graphics.fillRoundRect(x, y, width, height, corner, corner);

        graphics.setColor(palette.border());
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawRoundRect(x + 1, y + 1, width - 2, height - 2, corner, corner);

        int iconBlockWidth = drawProfileIcons(graphics, entry, x + 24, y, height, palette);
        drawRankBadge(graphics, entry.position(), x + 16, y + 12, palette);

        int textX = x + 24 + iconBlockWidth + 20;
        graphics.setColor(NAME_COLOR);
        graphics.setFont(uiFont(Font.BOLD, 34));
        drawTruncatedText(graphics, entry.label(), textX, y + 48, width - (textX - x) - 24);

        if (entry.detail() != null && !entry.detail().isBlank()) {
            graphics.setColor(META_COLOR);
            graphics.setFont(uiFont(Font.PLAIN, 24));
            int detailX = textX;
            if (entry.rankEmblem() != null) {
                int emblemSize = 34;
                int emblemY = y + height - emblemSize - 18;
                graphics.drawImage(entry.rankEmblem(), detailX, emblemY, emblemSize, emblemSize, null);
                detailX += emblemSize + 12;
            }
            drawTruncatedText(graphics, entry.detail(), detailX, y + 84, width - (detailX - x) - 24);
        }
    }

    private static int drawProfileIcons(
            Graphics2D graphics,
            PodiumEntry entry,
            int x,
            int y,
            int height,
            PodiumPalette palette
    ) {
        List<BufferedImage> icons = entry.profileIcons();
        if (icons.isEmpty()) {
            int iconSize = 72;
            int iconY = y + (height - iconSize) / 2;
            drawPlaceholderIcon(graphics, entry.label(), x, iconY, iconSize, palette.border());
            return iconSize;
        }

        if (icons.size() == 1) {
            int iconSize = 72;
            int iconY = y + (height - iconSize) / 2;
            drawCircularIcon(graphics, icons.get(0), x, iconY, iconSize, palette.border());
            return iconSize;
        }

        int iconSize = 56;
        int overlap = 18;
        int iconY = y + (height - iconSize) / 2;
        drawCircularIcon(graphics, icons.get(0), x, iconY, iconSize, palette.border());
        drawCircularIcon(graphics, icons.get(1), x + iconSize - overlap, iconY, iconSize, palette.border());
        return iconSize * 2 - overlap;
    }

    private static void drawRankBadge(Graphics2D graphics, int position, int x, int y, PodiumPalette palette) {
        int badgeSize = 30;
        graphics.setColor(palette.badge());
        graphics.fillOval(x, y, badgeSize, badgeSize);
        graphics.setColor(palette.border());
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawOval(x, y, badgeSize, badgeSize);
        graphics.setColor(palette.badgeText());
        graphics.setFont(uiFont(Font.BOLD, 18));
        FontMetrics metrics = graphics.getFontMetrics();
        String rankLabel = String.valueOf(position);
        int rankWidth = metrics.stringWidth(rankLabel);
        graphics.drawString(rankLabel, x + (badgeSize - rankWidth) / 2, y + 21);
    }

    private static void drawCircularIcon(
            Graphics2D graphics,
            BufferedImage icon,
            int x,
            int y,
            int size,
            Color borderColor
    ) {
        graphics.setColor(new Color(255, 255, 255, 24));
        graphics.fillOval(x, y, size, size);

        var clip = graphics.getClip();
        graphics.clip(new Ellipse2D.Float(x, y, size, size));
        graphics.drawImage(icon, x, y, size, size, null);
        graphics.setClip(clip);

        graphics.setColor(borderColor);
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawOval(x, y, size, size);
    }

    private static void drawPlaceholderIcon(
            Graphics2D graphics,
            String label,
            int x,
            int y,
            int size,
            Color borderColor
    ) {
        graphics.setColor(PLACEHOLDER_FILL);
        graphics.fillOval(x, y, size, size);
        graphics.setColor(borderColor);
        graphics.setStroke(new BasicStroke(2f));
        graphics.drawOval(x, y, size, size);

        String initial = initialFromLabel(label);
        graphics.setColor(NAME_COLOR);
        graphics.setFont(uiFont(Font.BOLD, size / 2));
        FontMetrics metrics = graphics.getFontMetrics();
        int initialWidth = metrics.stringWidth(initial);
        graphics.drawString(initial, x + (size - initialWidth) / 2, y + (size + metrics.getAscent()) / 2 - 4);
    }

    private static String initialFromLabel(String label) {
        if (label == null || label.isBlank()) {
            return "?";
        }
        int separator = label.indexOf('·');
        String primary = separator >= 0 ? label.substring(0, separator).trim() : label.trim();
        if (primary.isEmpty()) {
            return "?";
        }
        return primary.substring(0, 1).toUpperCase();
    }

    private static void drawBrandMark(Graphics2D graphics) {
        String brandLabel = "Rift Challenge";
        Font labelFont = brandFont(24f);
        graphics.setFont(labelFont);
        FontMetrics metrics = graphics.getFontMetrics();

        int logoSize = 44;
        int gap = 14;
        int textWidth = metrics.stringWidth(brandLabel);
        int blockWidth = (BRAND_LOGO != null ? logoSize + gap : 0) + textWidth;
        int right = WIDTH - 56;
        int left = right - blockWidth;
        int baseline = HEIGHT - 42;
        int logoY = baseline - logoSize + 8;

        if (BRAND_LOGO != null) {
            graphics.drawImage(BRAND_LOGO, left, logoY, logoSize, logoSize, null);
            left += logoSize + gap;
        }

        graphics.setColor(BRAND_COLOR);
        graphics.drawString(brandLabel, left, baseline);
    }

    private static void drawTruncatedText(Graphics2D graphics, String text, int x, int y, int maxWidth) {
        FontMetrics metrics = graphics.getFontMetrics();
        String value = text == null ? "" : text;
        if (metrics.stringWidth(value) <= maxWidth) {
            graphics.drawString(value, x, y);
            return;
        }

        String ellipsis = "…";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        while (value.length() > 1 && metrics.stringWidth(value) + ellipsisWidth > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        graphics.drawString(value + ellipsis, x, y);
    }

    private static PodiumPalette paletteFor(int position) {
        return switch (position) {
            case 1 -> new PodiumPalette(
                    new Color(197, 160, 89, 28),
                    new Color(216, 188, 130),
                    new Color(201, 149, 106),
                    new Color(26, 20, 14)
            );
            case 2 -> new PodiumPalette(
                    new Color(196, 202, 214, 30),
                    new Color(216, 221, 232),
                    new Color(168, 176, 191),
                    new Color(18, 23, 29)
            );
            default -> new PodiumPalette(
                    new Color(176, 122, 78, 36),
                    new Color(201, 149, 106),
                    new Color(150, 96, 62),
                    new Color(26, 18, 12)
            );
        };
    }

    private static Font brandFont(float size) {
        if (BRAND_FONT != null) {
            return BRAND_FONT.deriveFont(Font.PLAIN, size);
        }
        return uiFont(Font.BOLD, Math.round(size));
    }

    private static Font uiFont(int style, int size) {
        return new Font(Font.SANS_SERIF, style, size);
    }

    private static Font loadBrandFont() {
        try (InputStream stream = ChallengeOpenGraphImageRenderer.class.getResourceAsStream(
                "/brand/LemonMilk-Regular.ttf"
        )) {
            if (stream == null) {
                return null;
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font;
        } catch (Exception exception) {
            log.debug("Falling back to system font: brand font failed to load", exception);
            return null;
        }
    }

    private static BufferedImage loadBrandLogo() {
        try (InputStream stream = ChallengeOpenGraphImageRenderer.class.getResourceAsStream("/brand/logo.png")) {
            if (stream == null) {
                return null;
            }
            return ImageIO.read(stream);
        } catch (IOException exception) {
            log.debug("Falling back to no logo: brand logo failed to load", exception);
            return null;
        }
    }

    record ChallengeOpenGraphPreview(String challengeName, String subtitle, List<PodiumEntry> podium) {
    }

    record PodiumEntry(
            int position,
            String label,
            String detail,
            List<BufferedImage> profileIcons,
            BufferedImage rankEmblem
    ) {
        PodiumEntry {
            profileIcons = profileIcons == null ? List.of() : List.copyOf(profileIcons);
        }
    }

    private record PodiumPalette(Color background, Color border, Color badge, Color badgeText) {
    }
}
