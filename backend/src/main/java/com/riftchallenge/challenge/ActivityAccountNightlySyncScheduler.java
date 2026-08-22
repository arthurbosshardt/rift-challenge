package com.riftchallenge.challenge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Passive catch-up for every linked account so Mes statistiques is warm before the next visit.
 */
@Component
@Lazy(false)
public class ActivityAccountNightlySyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActivityAccountNightlySyncScheduler.class);

    private final ActivityAccountSyncOrchestrator syncOrchestrator;

    public ActivityAccountNightlySyncScheduler(ActivityAccountSyncOrchestrator syncOrchestrator) {
        this.syncOrchestrator = syncOrchestrator;
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void syncLinkedAccounts() {
        log.info("Starting nightly activity sync for linked accounts");
        syncOrchestrator.scheduleSyncForAllLinkedAccounts();
        log.info("Nightly activity sync scheduled for linked accounts");
    }
}
