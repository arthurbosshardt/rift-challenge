package com.riftchallenge.leaderboard;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaderboardAccountRankHistoryRepository extends JpaRepository<LeaderboardAccountRankHistory, UUID> {

    /**
     * The most recent snapshot at or before {@code cutoff} for one account — the real baseline a
     * window's LP-gained figure should replay against, when one exists yet.
     */
    Optional<LeaderboardAccountRankHistory> findFirstByRiotPuuidAndCapturedAtLessThanEqualOrderByCapturedAtDesc(
            String riotPuuid,
            Instant cutoff
    );
}
