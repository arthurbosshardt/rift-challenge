package com.riftrace.riot;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RiotProperties.class)
public class RiotConfig {

    @Bean
    RestClient riotRestClient(RiotProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader("X-Riot-Token", properties.apiKey())
                .build();
    }
}
