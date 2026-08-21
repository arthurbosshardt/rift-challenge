package com.riftchallenge.challenge;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeRefreshRepository extends JpaRepository<ChallengeRefresh, UUID> {

    Optional<ChallengeRefresh> findByChallengeId(UUID challengeId);

    /**
     * Atomically claims the refresh slot for a challenge: inserts the row if absent,
     * or bumps {@code refreshed_at} only if the existing one is already past the
     * cooldown floor. Two concurrent callers racing this statement can never both
     * win — Postgres serializes the upsert per row, closing the check-then-act
     * window that existed between a separate read and a later write.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO challenge_refresh (id, challenge_id, refreshed_at)
                    VALUES (:id, :challengeId, :refreshedAt)
                    ON CONFLICT (challenge_id) DO UPDATE
                        SET refreshed_at = EXCLUDED.refreshed_at
                        WHERE challenge_refresh.refreshed_at <= :cooldownFloor
                    """,
            nativeQuery = true
    )
    int claimRefresh(
            @Param("id") UUID id,
            @Param("challengeId") UUID challengeId,
            @Param("refreshedAt") Instant refreshedAt,
            @Param("cooldownFloor") Instant cooldownFloor
    );
}
