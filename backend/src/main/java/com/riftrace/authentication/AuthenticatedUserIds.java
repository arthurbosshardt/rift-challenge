package com.riftrace.authentication;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

public final class AuthenticatedUserIds {

    private AuthenticatedUserIds() {
    }

    public static UUID requireOwnerId(Authentication authentication) {
        UUID ownerId = optionalOwnerId(authentication);
        if (ownerId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ownerId;
    }

    public static UUID optionalOwnerId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof UUID ownerId) {
            return ownerId;
        }

        return null;
    }
}
