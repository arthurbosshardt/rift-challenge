package com.riftchallenge.summoner;

import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummonerSearchService {

    private static final int LIMIT = 8;

    private final ChallengeParticipantRepository participantRepository;
    private final UserRiotAccountRepository userRiotAccountRepository;

    public SummonerSearchService(
            ChallengeParticipantRepository participantRepository,
            UserRiotAccountRepository userRiotAccountRepository
    ) {
        this.participantRepository = participantRepository;
        this.userRiotAccountRepository = userRiotAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<SummonerSuggestionResponse> search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < 2) {
            return List.of();
        }

        var page = PageRequest.of(0, LIMIT);
        Map<String, SummonerSuggestionResponse> byPuuid = new LinkedHashMap<>();

        for (ChallengeParticipant participant : participantRepository.searchByRiotId(query, page)) {
            putIfAbsent(byPuuid, participant.getRiotPuuid(), participant.getRiotGameName(),
                    participant.getRiotTagLine(), participant.getProfileIconId());
        }
        for (UserRiotAccount account : userRiotAccountRepository.searchByRiotId(query, page)) {
            putIfAbsent(byPuuid, account.getRiotPuuid(), account.getRiotGameName(),
                    account.getRiotTagLine(), account.getProfileIconId());
        }

        return new ArrayList<>(byPuuid.values()).stream().limit(LIMIT).toList();
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
