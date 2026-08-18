package com.riftchallenge.challenge;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ChallengeSharePaths {

    private ChallengeSharePaths() {
    }

    public static String encodeSlug(String shareSlug) {
        return URLEncoder.encode(shareSlug, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String decodeSlug(String rawSlug) {
        return URLDecoder.decode(rawSlug, StandardCharsets.UTF_8);
    }

    public static String buildSharePath(String shareSlug) {
        return "/challenges/" + encodeSlug(shareSlug);
    }
}
