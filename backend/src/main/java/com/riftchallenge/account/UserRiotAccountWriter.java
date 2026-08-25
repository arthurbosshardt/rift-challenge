package com.riftchallenge.account;

import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Performs the atomic write for linking a Riot account in its own transaction, separate from any
 * Riot API call. No pessimistic locking is needed here: the unique constraints on user_id and
 * riot_account_id already back the invariants this enforces.
 */
@Component
class UserRiotAccountWriter {

    private final UserRiotAccountRepository userRiotAccountRepository;
    private final RiotAccountService riotAccountService;

    UserRiotAccountWriter(
            UserRiotAccountRepository userRiotAccountRepository,
            RiotAccountService riotAccountService
    ) {
        this.userRiotAccountRepository = userRiotAccountRepository;
        this.riotAccountService = riotAccountService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    UserRiotAccount link(UUID userId, RiotAccountDto accountDto, Integer profileIconId) {
        if (userRiotAccountRepository.findByUserId(userId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Account already linked. Unlink it before linking a new one."
            );
        }

        RiotAccount riotAccount = riotAccountService.findOrCreate(accountDto, profileIconId);

        userRiotAccountRepository.findByRiotAccountPuuid(riotAccount.getRiotPuuid()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account linked to another user");
        });

        return userRiotAccountRepository.save(UserRiotAccount.create(userId, riotAccount));
    }
}
