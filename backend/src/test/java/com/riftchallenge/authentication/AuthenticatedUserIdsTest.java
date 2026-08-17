package com.riftchallenge.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

class AuthenticatedUserIdsTest {

    @Test
    void optionalOwnerId_returnsUuidPrincipal() {
        UUID userId = UUID.randomUUID();
        var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());

        assertThat(AuthenticatedUserIds.optionalOwnerId(authentication)).isEqualTo(userId);
    }

    @Test
    void optionalOwnerId_ignoresNonUuidPrincipal() {
        var authentication = new UsernamePasswordAuthenticationToken("not-a-uuid", null, List.of());

        assertThat(AuthenticatedUserIds.optionalOwnerId(authentication)).isNull();
        assertThat(AuthenticatedUserIds.optionalOwnerId(null)).isNull();
    }

    @Test
    void requireOwnerId_rejectsMissingAuthentication() {
        assertThatThrownBy(() -> AuthenticatedUserIds.requireOwnerId(null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401");
    }
}
