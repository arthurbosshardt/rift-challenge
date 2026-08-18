package com.riftchallenge.challenge;

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefresh(UUID challengeId, Instant refreshedAt) {
        ChallengeRefresh refresh = challengeRefreshRepository.findByChallengeId(challengeId)
                .orElseGet(() -> ChallengeRefresh.create(challengeId, refreshedAt));

        refresh.updateRefreshedAt(refreshedAt);
        challengeRefreshRepository.save(refresh);
        dataSyncService.touchDataSyncedAt(challengeId, refreshedAt);
    }
}
