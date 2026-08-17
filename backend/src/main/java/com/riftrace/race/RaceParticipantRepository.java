package com.riftrace.race;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaceParticipantRepository extends JpaRepository<RaceParticipant, UUID> {

    List<RaceParticipant> findByRaceIdOrderByCreatedAtAsc(UUID raceId);

    long countByRaceId(UUID raceId);

    boolean existsByRaceIdAndRiotPuuid(UUID raceId, String riotPuuid);

    Optional<RaceParticipant> findByIdAndRaceId(UUID id, UUID raceId);

    List<RaceParticipant> findByDuoIdOrderByCreatedAtAsc(UUID duoId);
}
