package com.riftrace.race;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaceDuoRepository extends JpaRepository<RaceDuo, UUID> {

    long countByRaceId(UUID raceId);

    List<RaceDuo> findByRaceIdOrderByCreatedAtAsc(UUID raceId);
}
