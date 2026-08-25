package com.riftchallenge.challenge.dto;

import java.util.List;
import java.util.UUID;

public record AccountRecentGamesResponse(
        UUID accountId,
        String gameName,
        String tagLine,
        Integer profileIconId,
        String tier,
        String rank,
        Integer leaguePoints,
        Integer wins,
        Integer losses,
        List<RecentGameResponse> games,
        List<ChampionStatResponse> champions,
        PlaystyleResponse playstyle,
        int syncedGames,
        int seasonGames,
        boolean seasonSyncComplete,
        boolean seasonSyncInProgress
) {
}
