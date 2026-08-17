package com.riftrace.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void parseOrigins_trimsAndDropsEmptyValues() {
        assertThat(AppConfig.parseOrigins(" https://rift-race-beta.vercel.app , http://localhost:4200 , "))
                .containsExactly("https://rift-race-beta.vercel.app", "http://localhost:4200");
    }
}
