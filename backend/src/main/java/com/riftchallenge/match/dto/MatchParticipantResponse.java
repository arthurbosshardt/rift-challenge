package com.riftchallenge.match.dto;

import java.util.List;

public record MatchParticipantResponse(
        String gameName,
        String tagLine,
        Integer profileIconId,
        Integer championId,
        String championIconUrl,
        int champLevel,
        String role,
        boolean win,
        int kills,
        int deaths,
        int assists,
        int cs,
        int goldEarned,
        long damageDealt,
        int visionScore,
        int wardsPlaced,
        String summoner1IconUrl,
        String summoner2IconUrl,
        List<MatchItemResponse> items,
        boolean isFocusPlayer
) {
}
