package com.riftrace.riot;

import com.riftrace.riot.dto.RiotMatchDetailDto;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RiotMatchClient {

    public static final int RANKED_SOLO_QUEUE_ID = 420;

    private final RestClient riotRestClient;
    private final RiotProperties properties;

    public RiotMatchClient(RestClient riotRestClient, RiotProperties properties) {
        this.riotRestClient = riotRestClient;
        this.properties = properties;
    }

    public List<String> getRankedSoloMatchIdsSince(String puuid, long startTimeEpochSeconds) {
        String url = "https://%s.api.riotgames.com/lol/match/v5/matches/by-puuid/%s/ids?startTime=%d&queue=%d&count=100"
                .formatted(properties.regionalRouting(), puuid, startTimeEpochSeconds, RANKED_SOLO_QUEUE_ID);

        try {
            String[] matchIds = riotRestClient.get()
                    .uri(url)
                    .retrieve()
                    .body(String[].class);

            if (matchIds == null || matchIds.length == 0) {
                return List.of();
            }
            return Arrays.asList(matchIds);
        } catch (HttpClientErrorException.NotFound exception) {
            return List.of();
        } catch (HttpClientErrorException.TooManyRequests exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Riot API rate limit reached");
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Riot API request failed");
        }
    }

    public RiotMatchDetailDto getMatch(String matchId) {
        String url = "https://%s.api.riotgames.com/lol/match/v5/matches/%s"
                .formatted(properties.regionalRouting(), matchId);

        try {
            RiotMatchDetailDto match = riotRestClient.get()
                    .uri(url)
                    .retrieve()
                    .body(RiotMatchDetailDto.class);

            if (match == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Riot match response was empty");
            }
            return match;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Riot match not found");
        } catch (HttpClientErrorException.TooManyRequests exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Riot API rate limit reached");
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Riot API request failed");
        }
    }
}
