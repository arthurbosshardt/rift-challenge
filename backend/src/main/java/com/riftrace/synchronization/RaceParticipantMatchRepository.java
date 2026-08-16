package com.riftrace.synchronization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RaceParticipantMatchRepository extends JpaRepository<RaceParticipantMatch, UUID> {

    boolean existsByParticipantIdAndRiotMatchId(UUID participantId, String riotMatchId);

    @Query("""
            SELECT COUNT(rpm)
            FROM RaceParticipantMatch rpm
            WHERE rpm.participantId = :participantId AND rpm.win = true
            """)
    long countWinsByParticipantId(@Param("participantId") UUID participantId);

    @Query("""
            SELECT COUNT(rpm)
            FROM RaceParticipantMatch rpm
            WHERE rpm.participantId = :participantId AND rpm.win = false
            """)
    long countLossesByParticipantId(@Param("participantId") UUID participantId);
}
