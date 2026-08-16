package com.riftrace.authentication;

import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public Map<String, String> me(Authentication authentication) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        return Map.of("userId", ownerId.toString());
    }
}
