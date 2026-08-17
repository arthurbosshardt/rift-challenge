package com.riftrace.account;

import com.riftrace.account.dto.LinkRiotAccountRequest;
import com.riftrace.account.dto.UserRiotAccountResponse;
import com.riftrace.riot.RiotAccountClient;
import com.riftrace.riot.RiotIdParser;
import com.riftrace.riot.RiotSummonerClient;
import com.riftrace.riot.dto.RiotAccountDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserRiotAccountService {

    static final int MAX_ACCOUNTS_PER_USER = 1;

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

    @Transactional(readOnly = true)
    public List<UserRiotAccountResponse> listAccounts(UUID userId) {
        return userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(UserRiotAccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserRiotAccountResponse> findLinkedAccount(UUID userId) {
        return userRiotAccountRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .findFirst()
                .map(UserRiotAccountResponse::from);
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

        Integer profileIconId = riotSummonerClient.findProfileIconId(account.puuid()).orElse(null);
        UserRiotAccount saved = userRiotAccountRepository.save(
                UserRiotAccount.create(userId, account, profileIconId)
        );
        return UserRiotAccountResponse.from(saved);
    }

    @Transactional
    public void unlinkAccount(UUID userId, UUID accountId) {
        UserRiotAccount account = userRiotAccountRepository.findByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked account not found"));
        userRiotAccountRepository.delete(account);
    }
}
