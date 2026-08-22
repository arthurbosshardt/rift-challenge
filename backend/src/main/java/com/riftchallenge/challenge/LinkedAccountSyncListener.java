package com.riftchallenge.challenge;

import com.riftchallenge.account.LinkedAccountSyncEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
class LinkedAccountSyncListener {

    private final ActivityAccountSyncOrchestrator syncOrchestrator;

    LinkedAccountSyncListener(ActivityAccountSyncOrchestrator syncOrchestrator) {
        this.syncOrchestrator = syncOrchestrator;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onAccountLinked(LinkedAccountSyncEvent event) {
        syncOrchestrator.scheduleSyncForAccount(event.account());
    }
}
