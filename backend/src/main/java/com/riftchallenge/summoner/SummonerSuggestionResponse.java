package com.riftchallenge.summoner;

public record SummonerSuggestionResponse(
        String puuid,
        String gameName,
        String tagLine,
        String riotId,
        Integer profileIconId
) {
}
