package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import org.junit.jupiter.api.Test;

class RiotMatchDetailDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesParticipantChampionId() throws Exception {
        String json = """
                {
                  "metadata": { "matchId": "EUW1_123" },
                  "info": {
                    "gameStartTimestamp": 1234567890000,
                    "queueId": 420,
                    "participants": [
                      {
                        "puuid": "abc",
                        "win": true,
                        "profileIcon": 1,
                        "championId": 103,
                        "championName": "Ahri"
                      }
                    ]
                  }
                }
                """;

        RiotMatchDetailDto match = objectMapper.readValue(json, RiotMatchDetailDto.class);

        assertThat(match.info().participants()).hasSize(1);
        assertThat(match.info().participants().getFirst().championId()).isEqualTo(103);
        assertThat(match.info().participants().getFirst().championName()).isEqualTo("Ahri");
    }
}
