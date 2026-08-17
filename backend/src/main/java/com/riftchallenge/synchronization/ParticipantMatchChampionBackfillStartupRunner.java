package com.riftchallenge.synchronization;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ParticipantMatchChampionBackfillStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ParticipantMatchChampionBackfillStartupRunner.class);

    private final ParticipantMatchChampionBackfillService championBackfillService;

    public ParticipantMatchChampionBackfillStartupRunner(
            ParticipantMatchChampionBackfillService championBackfillService
    ) {
        this.championBackfillService = championBackfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        CompletableFuture.runAsync(() -> {
            try {
                championBackfillService.backfillAll();
            } catch (RuntimeException ex) {
                log.warn("Champion ID backfill failed on startup: {}", ex.getMessage());
            }
        });
    }
}
