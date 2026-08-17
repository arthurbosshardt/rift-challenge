package com.riftchallenge.challenge;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRefreshRepository extends JpaRepository<ChallengeRefresh, UUID> {

    Optional<ChallengeRefresh> findByChallengeId(UUID challengeId);
}
