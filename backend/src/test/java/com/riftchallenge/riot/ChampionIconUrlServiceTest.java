package com.riftchallenge.riot;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChampionIconUrlServiceTest {

    private ChampionIconUrlService service;

    @BeforeEach
    void setUp() {
        service = new ChampionIconUrlService(new ObjectMapper());
        service.loadChampionCatalog();
    }

    @Test
    void buildApiPath_returnsNullForMissingChampionId() {
        assertThat(service.buildApiPath(null)).isNull();
        assertThat(service.buildApiPath(0)).isNull();
    }

    @Test
    void buildApiPath_returnsRelativeApiPath() {
        assertThat(service.buildApiPath(103)).isEqualTo("/api/champion-icons/103.png");
    }

    @Test
    void buildExternalUrl_usesBundledDdragonMapping() {
        assertThat(service.buildExternalUrl(103))
                .isEqualTo("https://ddragon.leagueoflegends.com/cdn/16.16.1/img/champion/Ahri.png");
    }
}
