package com.riftchallenge.challenge;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeRefreshRecordService {

    private final ChallengeRefreshRepository challengeRefreshRepository;
    private final ChallengeDataSyncService dataSyncService;

    public ChallengeRefreshRecordService(
            ChallengeRefreshRepository challengeRefreshRepository,
            ChallengeDataSyncService dataSyncService
    ) {
        this.challengeRefreshRepository = challengeRefreshRepository;
        this.dataSyncService = dataSyncService;
    }

    /**
     * Atomically claims the refresh cooldown slot. Returns false without side
     * effects if another request already refreshed this challenge within
     * {@code cooldown} — closes the race window a plain read-then-write check
     * would leave open between two concurrent callers.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryClaimRefresh(UUID challengeId, Instant now, Duration cooldown) {
        int claimed = challengeRefreshRepository.claimRefresh(
                UUID.randomUUID(),
                challengeId,
                now,
                now.minus(cooldown)
        );
        return claimed > 0;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefresh(UUID challengeId, Instant refreshedAt) {
        ChallengeRefresh refresh = challengeRefreshRepository.findByChallengeId(challengeId)
                .orElseGet(() -> ChallengeRefresh.create(challengeId, refreshedAt));

        refresh.updateRefreshedAt(refreshedAt);
        challengeRefreshRepository.save(refresh);
        dataSyncService.touchDataSyncedAt(challengeId, refreshedAt);
    }
}
