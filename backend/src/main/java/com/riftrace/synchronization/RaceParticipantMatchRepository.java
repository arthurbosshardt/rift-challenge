package com.riftrace.synchronization;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RaceParticipantMatchRepository extends JpaRepository<RaceParticipantMatch, UUID> {

    boolean existsByParticipantId(UUID participantId);

    long countByParticipantId(UUID participantId);

    boolean existsByParticipantIdAndRiotMatchId(UUID participantId, String riotMatchId);

    @Query("""
            SELECT rpm.riotMatchId AS matchId, rpm.win AS win
            FROM RaceParticipantMatch rpm
            WHERE rpm.participantId = :participantId
            """)
    List<ParticipantMatchOutcome> findOutcomesByParticipantId(@Param("participantId") UUID participantId);

    interface ParticipantMatchOutcome {
        String getMatchId();

        boolean isWin();
    }

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

    @Query("""
            SELECT rpm.riotMatchId
            FROM RaceParticipantMatch rpm
            WHERE rpm.participantId = :participantId
            """)
    List<String> findMatchIdsByParticipantId(@Param("participantId") UUID participantId);

    @Query("""
            SELECT COUNT(rpm)
            FROM RaceParticipantMatch rpm
            WHERE rpm.participantId = :participantId
              AND rpm.riotMatchId IN :matchIds
              AND rpm.win = true
            """)
    long countWinsByParticipantIdAndMatchIds(
            @Param("participantId") UUID participantId,
            @Param("matchIds") List<String> matchIds
    );

    @Query("""
            SELECT COUNT(rpm)
            FROM RaceParticipantMatch rpm
            WHERE rpm.participantId = :participantId
              AND rpm.riotMatchId IN :matchIds
              AND rpm.win = false
            """)
    long countLossesByParticipantIdAndMatchIds(
            @Param("participantId") UUID participantId,
            @Param("matchIds") List<String> matchIds
    );
}
