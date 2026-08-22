package com.riftchallenge.challenge.dto;

public record ChampionStatResponse(
        Integer championId,
        String championIconUrl,
        String championName,
        int games,
        int wins,
        double winRate,
        double avgKills,
        double avgDeaths,
        double avgAssists,
        double kda,
        int avgCs,
        double avgCsPerMin
) {
}
