package com.riftrace.riot;

import com.riftrace.riot.dto.RiotAccountDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RiotAccountClient {

    private final RestClient riotRestClient;
    private final RiotProperties properties;

    public RiotAccountClient(RestClient riotRestClient, RiotProperties properties) {
        this.riotRestClient = riotRestClient;
        this.properties = properties;
    }

    public RiotAccountDto getAccountByRiotId(String gameName, String tagLine) {
        try {
            return riotRestClient.get()
                    .uri(
                            "https://{routing}.api.riotgames.com/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}",
                            properties.regionalRouting(),
                            gameName,
                            tagLine
                    )
                    .retrieve()
                    .body(RiotAccountDto.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Riot account not found");
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
