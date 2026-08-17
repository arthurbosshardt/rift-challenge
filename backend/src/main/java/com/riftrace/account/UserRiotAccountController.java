package com.riftrace.account;

import com.riftrace.account.dto.LinkRiotAccountRequest;
import com.riftrace.account.dto.UserRiotAccountResponse;
import com.riftrace.authentication.AuthenticatedUserIds;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/riot-accounts")
public class UserRiotAccountController {

    private final UserRiotAccountService userRiotAccountService;

    public UserRiotAccountController(UserRiotAccountService userRiotAccountService) {
        this.userRiotAccountService = userRiotAccountService;
    }

    @GetMapping
    public List<UserRiotAccountResponse> listAccounts(Authentication authentication) {
        UUID userId = AuthenticatedUserIds.requireOwnerId(authentication);
        return userRiotAccountService.listAccounts(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserRiotAccountResponse linkAccount(
            Authentication authentication,
            @Valid @RequestBody LinkRiotAccountRequest request
    ) {
        UUID userId = AuthenticatedUserIds.requireOwnerId(authentication);
        return userRiotAccountService.linkAccount(userId, request);
    }

    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlinkAccount(Authentication authentication, @PathVariable UUID accountId) {
        UUID userId = AuthenticatedUserIds.requireOwnerId(authentication);
        userRiotAccountService.unlinkAccount(userId, accountId);
    }
}
