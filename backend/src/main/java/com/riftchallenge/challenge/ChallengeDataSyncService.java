package com.riftchallenge.challenge;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeDataSyncService {

    private final ChallengeRepository challengeRepository;

    public ChallengeDataSyncService(ChallengeRepository challengeRepository) {
        this.challengeRepository = challengeRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchDataSyncedAt(UUID challengeId, Instant syncedAt) {
        challengeRepository.findById(challengeId).ifPresent(challenge -> {
            Instant effectiveSyncAt = syncedAt == null ? Instant.now() : syncedAt;
            if (challenge.getDataSyncedAt() != null && !effectiveSyncAt.isAfter(challenge.getDataSyncedAt())) {
                return;
            }
            challenge.updateDataSyncedAt(effectiveSyncAt);
            challengeRepository.save(challenge);
        });
    }
}
