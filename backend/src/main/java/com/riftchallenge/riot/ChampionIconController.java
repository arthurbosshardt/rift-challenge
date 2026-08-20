package com.riftchallenge.riot;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/champion-icons")
public class ChampionIconController {

    private final ChampionIconUrlService championIconUrlService;
    private final RestClient restClient;
    private final Map<Integer, byte[]> iconCache = new ConcurrentHashMap<>();

    public ChampionIconController(ChampionIconUrlService championIconUrlService) {
        this.championIconUrlService = championIconUrlService;
        this.restClient = RestClient.create();
    }

    @GetMapping("/{championId}.png")
    public ResponseEntity<byte[]> championIcon(@PathVariable int championId) {
        if (championId <= 0) {
            return ResponseEntity.notFound().build();
        }

        byte[] cached = iconCache.get(championId);
        if (cached != null) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .contentType(MediaType.IMAGE_PNG)
                    .body(cached);
        }

        String sourceUrl = championIconUrlService.buildExternalUrl(championId);
        if (sourceUrl == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            byte[] image = restClient.get()
                    .uri(sourceUrl)
                    .retrieve()
                    .body(byte[].class);
            if (image == null || image.length == 0) {
                return ResponseEntity.notFound().build();
            }

            iconCache.put(championId, image);

            return ResponseEntity.ok()
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .contentType(MediaType.IMAGE_PNG)
                    .body(image);
        } catch (RuntimeException exception) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
    }
}
