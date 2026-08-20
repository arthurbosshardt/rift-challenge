package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class OpenGraphRequestThrottleTest {

    @Test
    void enforce_allowsHtmlAndImageIndependentlyThenBlocksSameResource() {
        OpenGraphRequestThrottle throttle = new OpenGraphRequestThrottle(
                Clock.fixed(Instant.parse("2026-08-17T10:00:00Z"), ZoneOffset.UTC)
        );
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("203.0.113.10");

        assertThatCode(() -> throttle.enforce(request, "html")).doesNotThrowAnyException();
        assertThatCode(() -> throttle.enforce(request, "image")).doesNotThrowAnyException();
        assertThatThrownBy(() -> throttle.enforce(request, "html"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Please wait before requesting another preview");
    }
}
