package com.riftchallenge.riot;

import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RiotLeagueClient {

    public static final String RANKED_SOLO_QUEUE = "RANKED_SOLO_5x5";

    private final RestClient riotRestClient;
    private final RiotProperties properties;

    public RiotLeagueClient(RestClient riotRestClient, RiotProperties properties) {
        this.riotRestClient = riotRestClient;
        this.properties = properties;
    }

    public Optional<RiotLeagueEntryDto> findRankedSoloEntry(String puuid) {
        String url = "https://%s.api.riotgames.com/lol/league/v4/entries/by-puuid/%s"
                .formatted(properties.platform(), puuid);

        try {
            RiotLeagueEntryDto[] entries = riotRestClient.get()
                    .uri(url)
                    .retrieve()
                    .body(RiotLeagueEntryDto[].class);

            if (entries == null || entries.length == 0) {
                return Optional.empty();
            }

            return Arrays.stream(entries)
                    .filter(entry -> RANKED_SOLO_QUEUE.equals(entry.queueType()))
                    .findFirst();
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (HttpClientErrorException.TooManyRequests exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Riot API rate limit reached");
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Riot API request failed");
        }
    }
}
