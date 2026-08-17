package com.riftchallenge.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public record RiotMatchDetailDto(
        Metadata metadata,
        Info info
) {

    public record Metadata(String matchId) {
    }

    public record Info(
            long gameStartTimestamp,
            int queueId,
            List<Participant> participants
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(
            String puuid,
            boolean win,
            Integer profileIcon,
            Integer championId,
            String championName
    ) {
    }
}
