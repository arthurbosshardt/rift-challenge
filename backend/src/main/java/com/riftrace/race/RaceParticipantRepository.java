package com.riftrace.race;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RaceParticipantRepository extends JpaRepository<RaceParticipant, UUID> {

    List<RaceParticipant> findByRaceIdOrderByCreatedAtAsc(UUID raceId);

    long countByRaceId(UUID raceId);

    boolean existsByRaceIdAndRiotPuuid(UUID raceId, String riotPuuid);

    Optional<RaceParticipant> findByIdAndRaceId(UUID id, UUID raceId);

    List<RaceParticipant> findByDuoIdOrderByCreatedAtAsc(UUID duoId);

    @Query("""
            SELECT DISTINCT rp.raceId
            FROM RaceParticipant rp
            WHERE rp.riotPuuid IN :puuids
            """)
    List<UUID> findDistinctRaceIdsByRiotPuuidIn(@Param("puuids") Collection<String> puuids);

    @Query("""
            SELECT DISTINCT rp.raceId
            FROM RaceParticipant rp
            JOIN Race r ON r.id = rp.raceId
            WHERE r.isPublic = true
              AND r.startAt <= :now
              AND LOWER(REPLACE(rp.riotGameName, ' ', '')) LIKE CONCAT('%', :query, '%')
            """)
    List<UUID> findDistinctPublicRaceIdsByParticipantSearch(@Param("now") Instant now, @Param("query") String query);
}