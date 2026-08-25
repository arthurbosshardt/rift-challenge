package com.riftchallenge.account;

import com.riftchallenge.account.dto.BulkRegisterRiotAccountsRequest;
import com.riftchallenge.account.dto.BulkRegisterRiotAccountsResponse;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotIdParser;
import com.riftchallenge.riot.RiotSummonerClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RiotAccountService {

    private final RiotAccountRepository riotAccountRepository;
    private final RiotAccountClient riotAccountClient;
    private final RiotSummonerClient riotSummonerClient;

    public RiotAccountService(
            RiotAccountRepository riotAccountRepository,
            RiotAccountClient riotAccountClient,
            RiotSummonerClient riotSummonerClient
    ) {
        this.riotAccountRepository = riotAccountRepository;
        this.riotAccountClient = riotAccountClient;
        this.riotSummonerClient = riotSummonerClient;
    }

    public ResolvedRiotAccount resolveRiotAccount(String riotId) {
        RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse(riotId);
        RiotAccountDto account = riotAccountClient.getAccountByRiotId(parsed.gameName(), parsed.tagLine());
        Integer profileIconId = riotSummonerClient.findProfileIconId(account.puuid(), ChallengeRegion.EUW).orElse(null);
        return new ResolvedRiotAccount(account, profileIconId);
    }

    @Transactional
    public RiotAccount findOrCreate(RiotAccountDto account, Integer profileIconId) {
        return riotAccountRepository.findByRiotPuuid(account.puuid())
                .map(existing -> {
                    existing.updateIdentity(account.gameName(), account.tagLine(), profileIconId);
                    return riotAccountRepository.save(existing);
                })
                .orElseGet(() -> riotAccountRepository.save(RiotAccount.create(account, profileIconId)));
    }

    public BulkRegisterRiotAccountsResponse registerBulk(BulkRegisterRiotAccountsRequest request) {
        List<String> created = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (String riotId : request.riotIds()) {
            if (riotId == null || riotId.isBlank()) {
                continue;
            }
            String normalized = riotId.trim();
            try {
                ResolvedRiotAccount resolved = resolveRiotAccount(normalized);
                boolean alreadyTracked = riotAccountRepository.findByRiotPuuid(resolved.account().puuid()).isPresent();
                RiotAccount account = findOrCreate(resolved.account(), resolved.profileIconId());
                String label = account.getRiotGameName() + "#" + account.getRiotTagLine();
                if (alreadyTracked) {
                    existing.add(label);
                } else {
                    created.add(label);
                }
            } catch (RuntimeException exception) {
                errors.add(normalized + ": " + exception.getMessage());
            }
        }

        return new BulkRegisterRiotAccountsResponse(created, existing, errors);
    }

    public record ResolvedRiotAccount(RiotAccountDto account, Integer profileIconId) {
    }
}
