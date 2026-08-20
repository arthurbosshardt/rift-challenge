package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ChallengeRefreshRequestThrottleTest {

    private ChallengeRefreshRequestThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = new ChallengeRefreshRequestThrottle(
                Clock.fixed(Instant.parse("2026-08-17T10:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void enforce_allowsFirstRequest() {
        assertThatCode(() -> throttle.enforce("client-a")).doesNotThrowAnyException();
    }

    @Test
    void enforce_blocksSecondRequestWithinFiveSeconds() {
        throttle.enforce("client-a");

        assertThatThrownBy(() -> throttle.enforce("client-a"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Please wait before refreshing again");
    }

    @Test
    void enforce_allowsDifferentClientsIndependently() {
        throttle.enforce("client-a");

        assertThatCode(() -> throttle.enforce("client-b")).doesNotThrowAnyException();
    }

    @Test
    void enforce_allowsRequestAfterFiveSeconds() {
        MutableClock clock = new MutableClock(Instant.parse("2026-08-17T10:00:00Z"));
        ChallengeRefreshRequestThrottle advancingThrottle = new ChallengeRefreshRequestThrottle(clock);
        advancingThrottle.enforce("client-a");

        clock.setInstant(Instant.parse("2026-08-17T10:00:04Z"));
        assertThatThrownBy(() -> advancingThrottle.enforce("client-a"))
                .isInstanceOf(ResponseStatusException.class);

        clock.setInstant(Instant.parse("2026-08-17T10:00:05Z"));
        assertThatCode(() -> advancingThrottle.enforce("client-a")).doesNotThrowAnyException();
    }

    @Test
    void resolveClientKey_whenAuthenticated_usesUserId() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        UUID callerId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");

        assertThat(ChallengeRefreshRequestThrottle.resolveClientKey(request, callerId))
                .isEqualTo("user:11111111-1111-1111-1111-111111111111");
    }

    @Test
    void resolveClientKey_usesRightmostForwardedForValue() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.1");

        assertThat(ChallengeRefreshRequestThrottle.resolveClientKey(request, null)).isEqualTo("10.0.0.1");
    }

    @Test
    void resolveClientKey_ignoresSpoofedLeftmostForwardedFor() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("198.51.100.9, 203.0.113.50");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        assertThat(ChallengeRefreshRequestThrottle.resolveClientKey(request, null)).isEqualTo("203.0.113.50");
    }

    @Test
    void resolveClientKey_fallsBackToRemoteAddr() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(ChallengeRefreshRequestThrottle.resolveClientKey(request, null)).isEqualTo("127.0.0.1");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone = ZoneOffset.UTC;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
