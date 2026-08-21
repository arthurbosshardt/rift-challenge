package com.riftchallenge.riot;

import com.riftchallenge.riot.dto.RiotSummonerDto;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RiotSummonerClient {

    private final RestClient riotRestClient;
    private final RiotProperties properties;

    public RiotSummonerClient(RestClient riotRestClient, RiotProperties properties) {
        this.riotRestClient = riotRestClient;
        this.properties = properties;
    }

    public Optional<Integer> findProfileIconId(String puuid, ChallengeRegion region) {
        String url = "https://%s.api.riotgames.com/lol/summoner/v4/summoners/by-puuid/%s"
                .formatted(region.platform(), puuid);

        try {
            RiotSummonerDto summoner = riotRestClient.get()
                    .uri(url)
                    .retrieve()
                    .body(RiotSummonerDto.class);

            if (summoner == null) {
                return Optional.empty();
            }
            return Optional.of(summoner.profileIconId());
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (HttpClientErrorException.TooManyRequests exception) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Riot API rate limit reached");
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Riot API request failed"
            );
        }
    }
}
