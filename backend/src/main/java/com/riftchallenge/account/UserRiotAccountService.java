package com.riftchallenge.account;

import com.riftchallenge.account.dto.LinkRiotAccountRequest;
import com.riftchallenge.account.dto.UserRiotAccountResponse;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotIdParser;
import com.riftchallenge.riot.RiotSummonerClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserRiotAccountService {

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
        return userRiotAccountRepository.findByUserId(userId)
                .map(account -> List.of(enrichProfileIcon(account)))
                .orElseGet(List::of);
    }

    @Transactional
    public Optional<UserRiotAccountResponse> findLinkedAccount(UUID userId) {
        return userRiotAccountRepository.findByUserId(userId).map(this::enrichProfileIcon);
    }

    @Transactional(readOnly = true)
    public String resolveOwnedAccountPuuid(UUID userId, UUID accountId) {
        return userRiotAccountRepository.findByIdAndUserId(accountId, userId)
                .map(UserRiotAccount::getRiotPuuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked account not found"));
    }

    @Transactional(readOnly = true)
    public List<String> listLinkedPuids(UUID userId) {
        return userRiotAccountRepository.findByUserId(userId)
                .map(account -> List.of(account.getRiotPuuid()))
                .orElseGet(List::of);
    }

    @Transactional
    public UserRiotAccountResponse linkAccount(UUID userId, LinkRiotAccountRequest request) {
        if (userRiotAccountRepository.findByUserId(userId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Account already linked. Unlink it before linking a new one."
            );
        }

        RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse(request.riotId());
        RiotAccountDto account = riotAccountClient.getAccountByRiotId(parsed.gameName(), parsed.tagLine());

        userRiotAccountRepository.findByRiotPuuid(account.puuid()).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Account linked to another user");
        });

        // Linked accounts aren't region-tagged yet (unlike challenges); assumes EUW.
        Integer profileIconId = riotSummonerClient.findProfileIconId(account.puuid(), ChallengeRegion.EUW).orElse(null);
        UserRiotAccount saved = userRiotAccountRepository.save(
                UserRiotAccount.create(userId, account, profileIconId)
        );
        return enrichProfileIcon(saved);
    }

    @Transactional
    public void unlinkAccount(UUID userId, UUID accountId) {
        UserRiotAccount account = userRiotAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked account not found"));

        userRiotAccountRepository.delete(account);
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
