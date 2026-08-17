package com.riftrace.riot.dto;

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

    public record Participant(String puuid, boolean win, Integer profileIcon) {
    }
}
