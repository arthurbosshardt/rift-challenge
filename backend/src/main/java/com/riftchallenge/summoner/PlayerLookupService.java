package com.riftchallenge.summoner;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.account.RiotAccountService;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an exact riotId to the puuid/identity backing it, for the public player profile
 * page. Mirrors {@link SummonerSearchService}'s two sources: a challenge participant isn't
 * necessarily backed by a {@link RiotAccount} row (added via the Riot API without ever being
 * linked or leaderboard-synced), so both are checked. A participant-only match is also
 * registered into {@link RiotAccount} here, so viewing a player's profile is enough to pick
 * it up in the leaderboard's periodic sync.
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

    @Transactional
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

        return riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase(gameName, tagLine)
                .map(account -> toResponse(
                        account.getRiotPuuid(),
                        account.getRiotGameName(),
                        account.getRiotTagLine(),
                        account.getProfileIconId()
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
