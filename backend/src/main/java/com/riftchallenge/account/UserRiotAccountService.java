package com.riftchallenge.account;

import com.riftchallenge.account.dto.LinkRiotAccountRequest;
import com.riftchallenge.account.dto.UserRiotAccountResponse;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotSummonerClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserRiotAccountService {

    private final UserRiotAccountRepository userRiotAccountRepository;
    private final RiotAccountService riotAccountService;
    private final RiotSummonerClient riotSummonerClient;
    private final ApplicationEventPublisher eventPublisher;
    private final UserRiotAccountWriter accountWriter;

    public UserRiotAccountService(
            UserRiotAccountRepository userRiotAccountRepository,
            RiotAccountService riotAccountService,
            RiotSummonerClient riotSummonerClient,
            ApplicationEventPublisher eventPublisher,
            UserRiotAccountWriter accountWriter
    ) {
        this.userRiotAccountRepository = userRiotAccountRepository;
        this.riotAccountService = riotAccountService;
        this.riotSummonerClient = riotSummonerClient;
        this.eventPublisher = eventPublisher;
        this.accountWriter = accountWriter;
    }

    public List<UserRiotAccountResponse> listAccounts(UUID userId) {
        return userRiotAccountRepository.findByUserId(userId)
                .map(account -> List.of(enrichProfileIcon(account)))
                .orElseGet(List::of);
    }

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

    public UserRiotAccountResponse linkAccount(UUID userId, LinkRiotAccountRequest request) {
        if (userRiotAccountRepository.findByUserId(userId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Account already linked. Unlink it before linking a new one."
            );
        }

        RiotAccountService.ResolvedRiotAccount resolved = riotAccountService.resolveRiotAccount(request.riotId());

        UserRiotAccount saved = accountWriter.link(userId, resolved.account(), resolved.profileIconId());
        eventPublisher.publishEvent(new LinkedAccountSyncEvent(saved));
        return UserRiotAccountResponse.from(saved);
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
