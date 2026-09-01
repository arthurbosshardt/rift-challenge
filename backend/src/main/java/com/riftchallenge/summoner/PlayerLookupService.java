package com.riftchallenge.summoner;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.account.RiotAccountService;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Resolves an exact riotId to the puuid/identity backing it, for the public player profile
 * page. Mirrors {@link SummonerSearchService}'s two sources: a challenge participant isn't
 * necessarily backed by a {@link RiotAccount} row (added via the Riot API without ever being
 * linked or leaderboard-synced), so both are checked. A participant-only match is also
 * registered into {@link RiotAccount} here, so viewing a player's profile is enough to pick
 * it up in the leaderboard's periodic sync.
 *
 * <p>If neither local source has this player — nobody has ever added them to a challenge or
 * looked them up before — falls back to a live Riot lookup, so "any player" (per the product
 * docs) actually means any player, not just ones this app has already seen. The found account
 * is persisted the same way the participant-only branch already does, so it's a one-time Riot
 * call per player. Callers are expected to rate-limit this (see PlayerProfileRequestThrottle) —
 * it's the only branch here that can reach Riot's API.
 */
@Service
public class PlayerLookupService {

    private final ChallengeParticipantRepository participantRepository;
    private final RiotAccountRepository riotAccountRepository;
    private final RiotAccountService riotAccountService;

    public PlayerLookupService(
            ChallengeParticipantRepository participantRepository,
            RiotAccountRepository riotAccountRepository,
            RiotAccountService riotAccountService
    ) {
        this.participantRepository = participantRepository;
        this.riotAccountRepository = riotAccountRepository;
        this.riotAccountService = riotAccountService;
    }

    // Intentionally not @Transactional: resolveFromRiot() below calls out to Riot. Each
    // repository read/write already runs its own short-lived transaction (Spring Data default),
    // so no Hikari connection is held open across that network call.
    public Optional<SummonerSuggestionResponse> resolve(String gameName, String tagLine) {
        Optional<ChallengeParticipant> participant = participantRepository
                .findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc(gameName, tagLine);
        if (participant.isPresent()) {
            ChallengeParticipant p = participant.get();
            riotAccountService.findOrCreate(
                    new RiotAccountDto(p.getRiotPuuid(), p.getRiotGameName(), p.getRiotTagLine()),
                    p.getProfileIconId()
            );
            return Optional.of(toResponse(p.getRiotPuuid(), p.getRiotGameName(), p.getRiotTagLine(), p.getProfileIconId()));
        }

        Optional<RiotAccount> tracked = riotAccountRepository
                .findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase(gameName, tagLine);
        if (tracked.isPresent()) {
            RiotAccount account = tracked.get();
            return Optional.of(toResponse(
                    account.getRiotPuuid(),
                    account.getRiotGameName(),
                    account.getRiotTagLine(),
                    account.getProfileIconId()
            ));
        }

        return resolveFromRiot(gameName, tagLine);
    }

    private Optional<SummonerSuggestionResponse> resolveFromRiot(String gameName, String tagLine) {
        RiotAccountService.ResolvedRiotAccount resolved;
        try {
            resolved = riotAccountService.resolveExactRiotAccount(gameName, tagLine);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw exception;
        }

        RiotAccount saved = riotAccountService.findOrCreate(resolved.account(), resolved.profileIconId());
        return Optional.of(toResponse(
                saved.getRiotPuuid(),
                saved.getRiotGameName(),
                saved.getRiotTagLine(),
                saved.getProfileIconId()
        ));
    }

    private static SummonerSuggestionResponse toResponse(
            String puuid,
            String gameName,
            String tagLine,
            Integer profileIconId
    ) {
        return new SummonerSuggestionResponse(puuid, gameName, tagLine, gameName + "#" + tagLine, profileIconId);
    }
}
