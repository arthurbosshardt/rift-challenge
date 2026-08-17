package com.riftchallenge.synchronization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiotMatchRepository extends JpaRepository<RiotMatch, UUID> {

    Optional<RiotMatch> findByRiotMatchId(String riotMatchId);

    boolean existsByRiotMatchId(String riotMatchId);
}
