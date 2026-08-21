package com.riftchallenge.account;

import com.riftchallenge.account.dto.LinkRiotAccountRequest;
import com.riftchallenge.account.dto.UserRiotAccountResponse;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotIdParser;
import com.riftchallenge.riot.RiotSummonerClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserRiotAccountService {

    static final int MAX_ACCOUNTS_PER_USER = 10;

    private final UserRiotAccountRepository userRiotAccountRepository;
    private final RiotAccountClient riotAccountClient;
    private final RiotSummonerClient riotSummonerClient;

    public UserRiotAccountService(
            UserRiotAccountRepository userRiotAccountRepository,
            RiotAccountClient riotAccountClient,
            RiotSummonerClient riotSummonerClient
    ) {
        this.userRiotAccountRepository = userRiotAccountRepository;
        this.riotAccountClient = riotAccountClient;
        this.riotSummonerClient = riotSummonerClient;
    }

    @Transactional
    public List<UserRiotAccountResponse> listAccounts(UUID userId) {
        return userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .sorted(primaryFirst())
                .map(this::enrichProfileIcon)
                .toList();
    }

    @Transactional
    public Optional<UserRiotAccountResponse> findLinkedAccount(UUID userId) {
        return findPrimaryAccount(userId);
    }

    @Transactional
    public Optional<UserRiotAccountResponse> findPrimaryAccount(UUID userId) {
        return userRiotAccountRepository.findByUserIdAndPrimaryAccountTrue(userId)
                .or(() -> userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream().findFirst())
                .map(this::enrichProfileIcon);
    }

    @Transactional(readOnly = true)
    public String resolveOwnedAccountPuuid(UUID userId, UUID accountId) {
        return userRiotAccountRepository.findByIdAndUserId(accountId, userId)
                .map(UserRiotAccount::getRiotPuuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked account not found"));
    }

    @Transactional(readOnly = true)
    public List<String> listLinkedPuids(UUID userId) {
        return userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(UserRiotAccount::getRiotPuuid)
                .toList();
    }

    @Transactional
    public UserRiotAccountResponse linkAccount(UUID userId, LinkRiotAccountRequest request) {
        if (userRiotAccountRepository.countByUserId(userId) >= MAX_ACCOUNTS_PER_USER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Linked account limit reached");
        }

        boolean hasPrimary = userRiotAccountRepository.findByUserIdAndPrimaryAccountTrue(userId).isPresent();
        if (hasPrimary && !request.smurf()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Primary account already linked. Link additional accounts as smurfs."
            );
        }
        if (!hasPrimary && request.smurf()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Link a primary account before adding smurfs."
            );
        }

        RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse(request.riotId());
        RiotAccountDto account = riotAccountClient.getAccountByRiotId(parsed.gameName(), parsed.tagLine());

        if (userRiotAccountRepository.existsByUserIdAndRiotPuuid(userId, account.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account already linked");
        }

        userRiotAccountRepository.findByRiotPuuid(account.puuid()).ifPresent(existing -> {
            if (!existing.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Account linked to another user");
            }
        });

        // Linked accounts aren't region-tagged yet (unlike challenges); assumes EUW.
        Integer profileIconId = riotSummonerClient.findProfileIconId(account.puuid(), ChallengeRegion.EUW).orElse(null);
        boolean primaryAccount = !hasPrimary;
        UserRiotAccount saved = userRiotAccountRepository.save(
                UserRiotAccount.create(userId, account, profileIconId, primaryAccount)
        );
        return enrichProfileIcon(saved);
    }

    @Transactional
    public void unlinkAccount(UUID userId, UUID accountId) {
        UserRiotAccount account = userRiotAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked account not found"));

        if (account.isPrimaryAccount()) {
            promoteNextSmurfToPrimary(userId, account.getId());
        }

        userRiotAccountRepository.delete(account);
    }

    private void promoteNextSmurfToPrimary(UUID userId, UUID excludedAccountId) {
        userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .filter(candidate -> !candidate.getId().equals(excludedAccountId))
                .findFirst()
                .ifPresent(nextPrimary -> {
                    nextPrimary.promoteToPrimary();
                    userRiotAccountRepository.save(nextPrimary);
                });
    }

    private Comparator<UserRiotAccount> primaryFirst() {
        return Comparator.comparing(UserRiotAccount::isPrimaryAccount).reversed()
                .thenComparing(UserRiotAccount::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private UserRiotAccountResponse enrichProfileIcon(UserRiotAccount account) {
        if (account.getProfileIconId() != null) {
            return UserRiotAccountResponse.from(account);
        }

        try {
            return riotSummonerClient.findProfileIconId(account.getRiotPuuid(), ChallengeRegion.EUW)
                    .map(profileIconId -> {
                        account.updateProfileIconId(profileIconId);
                        return UserRiotAccountResponse.from(userRiotAccountRepository.save(account));
                    })
                    .orElseGet(() -> UserRiotAccountResponse.from(account));
        } catch (ResponseStatusException ignored) {
            return UserRiotAccountResponse.from(account);
        }
    }
}
