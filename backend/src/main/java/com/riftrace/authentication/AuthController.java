package com.riftrace.authentication;

import com.riftrace.account.AppUser;
import com.riftrace.account.AppUserRepository;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository appUserRepository;

    public AuthController(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @GetMapping("/me")
    public AuthMeResponse me(Authentication authentication) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        AppUser user = appUserRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
        return new AuthMeResponse(user.getId().toString(), user.getUsername());
    }
}
