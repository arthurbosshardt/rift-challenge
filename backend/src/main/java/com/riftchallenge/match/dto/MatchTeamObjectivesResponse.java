package com.riftchallenge.match.dto;

public record MatchTeamObjectivesResponse(
        int towerKills,
        int dragonKills,
        int baronKills,
        int riftHeraldKills,
        int inhibitorKills
) {
}
