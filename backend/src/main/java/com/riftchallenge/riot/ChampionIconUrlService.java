package com.riftchallenge.riot;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class ChampionIconUrlService {

    private static final String API_ICON_PATH = "/api/champion-icons/";

    private final ObjectMapper objectMapper;
    private Map<Integer, String> championKeysById = Map.of();

    public ChampionIconUrlService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadChampionCatalog() {
        try (InputStream inputStream = new ClassPathResource("champion-keys.json").getInputStream()) {
            Map<String, String> rawMapping = objectMapper.readValue(
                    inputStream,
                    new TypeReference<Map<String, String>>() {}
            );
            Map<Integer, String> mapping = new HashMap<>();
            rawMapping.forEach((key, championName) -> mapping.put(Integer.parseInt(key), championName));
            championKeysById = Map.copyOf(mapping);
        } catch (IOException | RuntimeException ignored) {
            championKeysById = Map.of();
        }
    }

    public String buildApiPath(Integer championId) {
        if (championId == null || championId <= 0) {
            return null;
        }
        return API_ICON_PATH + championId + ".png";
    }

    public String buildExternalUrl(Integer championId) {
        if (championId == null || championId <= 0) {
            return null;
        }

        String championKey = championKeysById.get(championId);
        if (championKey != null) {
            return "https://ddragon.leagueoflegends.com/cdn/%s/img/champion/%s.png"
                    .formatted(DDragonVersions.CURRENT, championKey);
        }

        return "https://raw.communitydragon.org/latest/plugins/rcp-be-lol-game-data/global/default/v1/champion-icons/%d.png"
                .formatted(championId);
    }
}
