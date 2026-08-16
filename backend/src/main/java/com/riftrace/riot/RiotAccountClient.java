package com.riftrace.riot;

import com.riftrace.riot.dto.RiotAccountDto;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        String encodedGameName = encodePathSegment(gameName);
        String encodedTagLine = encodePathSegment(tagLine);
        String url = "https://%s.api.riotgames.com/riot/account/v1/accounts/by-riot-id/%s/%s"
                .formatted(properties.regionalRouting(), encodedGameName, encodedTagLine);

        try {
            return riotRestClient.get()
                    .uri(url)
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

    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
