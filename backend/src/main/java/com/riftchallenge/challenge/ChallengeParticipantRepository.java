package com.riftchallenge.challenge;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeParticipantRepository extends JpaRepository<ChallengeParticipant, UUID> {

    List<ChallengeParticipant> findByChallengeIdOrderByCreatedAtAsc(UUID challengeId);

    long countByChallengeId(UUID challengeId);

    boolean existsByChallengeIdAndRiotPuuid(UUID challengeId, String riotPuuid);

    Optional<ChallengeParticipant> findByIdAndChallengeId(UUID id, UUID challengeId);

    List<ChallengeParticipant> findByDuoIdOrderByCreatedAtAsc(UUID duoId);

    @Query("""
            SELECT DISTINCT rp.challengeId
            FROM ChallengeParticipant rp
            WHERE rp.riotPuuid IN :puuids
            """)
    List<UUID> findDistinctChallengeIdsByRiotPuuidIn(@Param("puuids") Collection<String> puuids);

    @Query("""
            SELECT DISTINCT rp.challengeId
            FROM ChallengeParticipant rp
            JOIN Challenge r ON r.id = rp.challengeId
            WHERE r.isPublic = true
              AND r.startAt <= :now
              AND LOWER(REPLACE(rp.riotGameName, ' ', '')) LIKE CONCAT('%', :query, '%')
            """)
    List<UUID> findDistinctPublicChallengeIdsByParticipantSearch(@Param("now") Instant now, @Param("query") String query);
}