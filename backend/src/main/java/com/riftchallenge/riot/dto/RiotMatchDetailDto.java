package com.riftchallenge.riot.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public record RiotMatchDetailDto(
        Metadata metadata,
        Info info
) {

    public record Metadata(String matchId) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Info(
            long gameStartTimestamp,
            long gameDuration,
            int queueId,
            List<Participant> participants
    ) {
        public Info(long gameStartTimestamp, int queueId, List<Participant> participants) {
            this(gameStartTimestamp, 0L, queueId, participants);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Participant(
            String puuid,
            String riotIdGameName,
            String riotIdTagline,
            boolean win,
            Integer profileIcon,
            Integer championId,
            String championName,
            int champLevel,
            int teamId,
            String teamPosition,
            int kills,
            int deaths,
            int assists,
            int goldEarned,
            int totalMinionsKilled,
            int neutralMinionsKilled,
            long totalDamageDealtToChampions,
            int visionScore,
            int wardsPlaced,
            int wardsKilled,
            int summoner1Id,
            int summoner2Id,
            int item0,
            int item1,
            int item2,
            int item3,
            int item4,
            int item5,
            int item6
    ) {
        public Participant(String puuid, boolean win, Integer profileIcon, Integer championId, String championName) {
            this(
                    puuid, null, null, win, profileIcon, championId, championName,
                    // champLevel, teamId, teamPosition
                    0, 0, null,
                    // kills, deaths, assists, goldEarned, totalMinionsKilled, neutralMinionsKilled, totalDamageDealtToChampions
                    0, 0, 0, 0, 0, 0, 0L,
                    // visionScore, wardsPlaced, wardsKilled, summoner1Id, summoner2Id
                    0, 0, 0, 0, 0,
                    // item0..item6
                    0, 0, 0, 0, 0, 0, 0
            );
        }
    }
}
