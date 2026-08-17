package com.riftchallenge.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void parseOrigins_trimsAndDropsEmptyValues() {
        assertThat(AppConfig.parseOrigins(" https://app.example.com , http://localhost:4200 , "))
                .containsExactly("https://app.example.com", "http://localhost:4200");
    }
}
