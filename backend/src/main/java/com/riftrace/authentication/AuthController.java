package com.riftrace.authentication;

import com.riftrace.account.AppUser;
import com.riftrace.account.AppUserRepository;
import com.riftrace.account.UserRiotAccountService;
import com.riftrace.account.dto.UserRiotAccountResponse;
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
    private final UserRiotAccountService userRiotAccountService;

    public AuthController(
            AppUserRepository appUserRepository,
            UserRiotAccountService userRiotAccountService
    ) {
        this.appUserRepository = appUserRepository;
        this.userRiotAccountService = userRiotAccountService;
    }

    @GetMapping("/me")
    public AuthMeResponse me(Authentication authentication) {
        UUID ownerId = AuthenticatedUserIds.requireOwnerId(authentication);
        AppUser user = appUserRepository.findById(ownerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        AuthMeResponse.LinkedRiotAccount linkedRiotAccount = userRiotAccountService.findLinkedAccount(ownerId)
                .map(AuthController::toLinkedRiotAccount)
                .orElse(null);

        return new AuthMeResponse(user.getId().toString(), user.getUsername(), linkedRiotAccount);
    }

    private static AuthMeResponse.LinkedRiotAccount toLinkedRiotAccount(UserRiotAccountResponse account) {
        return new AuthMeResponse.LinkedRiotAccount(
                account.id().toString(),
                account.gameName(),
                account.tagLine(),
                account.riotId(),
                account.profileIconId()
        );
    }
}
