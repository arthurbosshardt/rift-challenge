package com.riftchallenge.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientRequestKeysTest {

    @Test
    void resolve_prefersAuthenticatedUserId() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID callerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");

        assertThat(ClientRequestKeys.resolve(request, callerId))
                .isEqualTo("user:11111111-1111-1111-1111-111111111111");
    }

    @Test
    void resolve_usesRightmostForwardedForHop() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9, 203.0.113.50");

        assertThat(ClientRequestKeys.resolve(request, null)).isEqualTo("203.0.113.50");
    }

    @Test
    void resolve_fallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(ClientRequestKeys.resolve(request, null)).isEqualTo("127.0.0.1");
    }
}
