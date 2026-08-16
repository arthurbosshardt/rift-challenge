package com.riftrace.riot.dto;

public record RiotLeagueEntryDto(
        String queueType,
        String tier,
        String rank,
        int leaguePoints,
        int wins,
        int losses
) {
}
