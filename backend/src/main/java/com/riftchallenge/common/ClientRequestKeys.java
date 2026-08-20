package com.riftchallenge.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;

/**
 * Builds stable throttle keys. Authenticated callers use their user id;
 * anonymous callers use the rightmost {@code X-Forwarded-For} hop (edge proxy)
 * instead of the leftmost client-controlled value.
 */
public final class ClientRequestKeys {

    private ClientRequestKeys() {
    }

    public static String resolve(HttpServletRequest request, UUID callerId) {
        if (callerId != null) {
            return "user:" + callerId;
        }

        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] hops = forwardedFor.split(",");
            return hops[hops.length - 1].trim();
        }

        return request.getRemoteAddr();
    }
}
