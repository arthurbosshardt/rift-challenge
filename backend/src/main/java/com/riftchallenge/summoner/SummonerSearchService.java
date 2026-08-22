package com.riftchallenge.summoner;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
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
    private final RiotAccountRepository riotAccountRepository;

    public SummonerSearchService(
            ChallengeParticipantRepository participantRepository,
            RiotAccountRepository riotAccountRepository
    ) {
        this.participantRepository = participantRepository;
        this.riotAccountRepository = riotAccountRepository;
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
        for (RiotAccount account : riotAccountRepository.searchByRiotId(query, page)) {
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
