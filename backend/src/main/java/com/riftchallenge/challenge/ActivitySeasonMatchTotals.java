package com.riftchallenge.challenge;

import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import java.util.Optional;

final class ActivitySeasonMatchTotals {

    static final int FALLBACK_LIMIT = 500;

    private ActivitySeasonMatchTotals() {
    }

    static int resolve(RiotLeagueClient riotLeagueClient, String puuid) {
        Optional<RiotLeagueEntryDto> currentRank = riotLeagueClient.findRankedSoloEntry(puuid, ChallengeRegion.EUW);
        return seasonMatchTotal(currentRank);
    }

    static int seasonMatchTotal(Optional<RiotLeagueEntryDto> currentRank) {
        return currentRank
                .map(rank -> rank.wins() + rank.losses())
                .filter(total -> total > 0)
                .orElse(FALLBACK_LIMIT);
    }
}
