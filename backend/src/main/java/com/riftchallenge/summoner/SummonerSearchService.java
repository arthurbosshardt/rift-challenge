package com.riftchallenge.summoner;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.riot.RiotIdParser;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SummonerSearchService {

    private static final int LIMIT = 8;

    private final ChallengeParticipantRepository participantRepository;
    private final RiotAccountRepository riotAccountRepository;
    private final PlayerLookupService playerLookupService;
    private final SummonerSearchRiotFallbackThrottle riotFallbackThrottle;

    public SummonerSearchService(
            ChallengeParticipantRepository participantRepository,
            RiotAccountRepository riotAccountRepository,
            PlayerLookupService playerLookupService,
            SummonerSearchRiotFallbackThrottle riotFallbackThrottle
    ) {
        this.participantRepository = participantRepository;
        this.riotAccountRepository = riotAccountRepository;
        this.playerLookupService = playerLookupService;
        this.riotFallbackThrottle = riotFallbackThrottle;
    }

    /**
     * {@code request}/{@code authentication} are only used if the local search comes up empty and
     * {@code rawQuery} turns out to be a complete Riot ID — see {@link #resolveFromRiotIfComplete}.
     */
    public List<SummonerSuggestionResponse> search(String rawQuery, HttpServletRequest request, Authentication authentication) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2) {
            return List.of();
        }

        List<SummonerSuggestionResponse> local = searchLocal(query);
        if (!local.isEmpty()) {
            return local;
        }

        return resolveFromRiotIfComplete(query, request, authentication).map(List::of).orElse(List.of());
    }

    private List<SummonerSuggestionResponse> searchLocal(String query) {
        var page = PageRequest.of(0, LIMIT);
        Map<String, SummonerSuggestionResponse> byPuuid = new LinkedHashMap<>();

        for (ChallengeParticipant participant : participantRepository.searchByRiotId(query, page)) {
            putIfAbsent(byPuuid, participant.getRiotPuuid(), participant.getRiotGameName(),
                    participant.getRiotTagLine(), participant.getProfileIconId());
        }
        for (RiotAccount account : riotAccountRepository.searchByRiotId(query, page)) {
            putIfAbsent(byPuuid, account.getRiotPuuid(), account.getRiotGameName(),
                    account.getRiotTagLine(), account.getProfileIconId());
        }

        return new ArrayList<>(byPuuid.values()).stream().limit(LIMIT).toList();
    }

    /**
     * The typeahead searches by partial gameName, which Riot's API can't do — it only resolves an
     * exact gameName#tagLine. So this only ever fires once the user has typed a complete-looking
     * Riot ID (query parses via {@link RiotIdParser}) and nothing local matched.
     *
     * <p>Deliberately fails open to "no suggestion" rather than surfacing an error: this runs on
     * every debounced keystroke of an automatic typeahead, not a deliberate user action, so a
     * throttle rejection, a Riot 404, or any other Riot error should all look the same to the
     * caller — nothing found this time.
     */
    private Optional<SummonerSuggestionResponse> resolveFromRiotIfComplete(
            String query,
            HttpServletRequest request,
            Authentication authentication
    ) {
        RiotIdParser.ParsedRiotId parsed;
        try {
            parsed = RiotIdParser.parse(query);
        } catch (ResponseStatusException exception) {
            return Optional.empty();
        }

        if (!riotFallbackThrottle.tryClaim(request, authentication)) {
            return Optional.empty();
        }

        try {
            return playerLookupService.resolve(parsed.gameName(), parsed.tagLine());
        } catch (ResponseStatusException exception) {
            return Optional.empty();
        }
    }

    private static void putIfAbsent(
            Map<String, SummonerSuggestionResponse> byPuuid,
            String puuid,
            String gameName,
            String tagLine,
            Integer profileIconId
    ) {
        if (puuid == null || byPuuid.containsKey(puuid)) {
            return;
        }
        byPuuid.put(puuid, new SummonerSuggestionResponse(
                puuid,
                gameName,
                tagLine,
                gameName + "#" + tagLine,
                profileIconId
        ));
    }
}
