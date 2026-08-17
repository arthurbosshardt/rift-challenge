package com.riftchallenge.challenge;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeDuoRepository extends JpaRepository<ChallengeDuo, UUID> {

    long countByChallengeId(UUID challengeId);

    List<ChallengeDuo> findByChallengeIdOrderByCreatedAtAsc(UUID challengeId);
}
