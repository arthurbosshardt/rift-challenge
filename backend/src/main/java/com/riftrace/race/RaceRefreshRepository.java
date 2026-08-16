package com.riftrace.race;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaceRefreshRepository extends JpaRepository<RaceRefresh, UUID> {

    Optional<RaceRefresh> findByRaceId(UUID raceId);
}
