package com.riftchallenge.riot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RiotProperties.class)
public class RiotConfig {

    @Bean
    RiotAppRateLimiter riotAppRateLimiter() {
        return new RiotAppRateLimiter();
    }

    @Bean
    RestClient riotRestClient(RiotProperties properties, RiotAppRateLimiter riotAppRateLimiter) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);

        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor(new RiotRateLimitRetryInterceptor(properties.maxRetryDelayMs()))
                .requestInterceptor(new RiotRateLimitingInterceptor(riotAppRateLimiter))
                .defaultHeader("X-Riot-Token", properties.apiKey())
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService riotSyncExecutor(RiotProperties properties) {
        int concurrency = Math.max(1, properties.syncConcurrency());
        return Executors.newFixedThreadPool(concurrency);
    }

    /** Dedicated so a long-running admin-triggered backfill never steals a {@link #riotSyncExecutor} slot from itself. */
    @Bean(destroyMethod = "shutdown")
    ExecutorService riotBackfillOrchestratorExecutor() {
        return Executors.newSingleThreadExecutor();
    }
}
