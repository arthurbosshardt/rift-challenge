package com.riftrace.race;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RaceRefreshRecordService {

    private final RaceRefreshRepository raceRefreshRepository;

    public RaceRefreshRecordService(RaceRefreshRepository raceRefreshRepository) {
        this.raceRefreshRepository = raceRefreshRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRefresh(UUID raceId, Instant refreshedAt) {
        RaceRefresh refresh = raceRefreshRepository.findByRaceId(raceId)
                .orElseGet(() -> RaceRefresh.create(raceId, refreshedAt));

        refresh.updateRefreshedAt(refreshedAt);
        raceRefreshRepository.save(refresh);
    }
}
