package com.riftchallenge.synchronization;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeParticipantMatchRepository extends JpaRepository<ChallengeParticipantMatch, UUID> {

    boolean existsByParticipantId(UUID participantId);

    long countByParticipantId(UUID participantId);

    boolean existsByParticipantIdAndRiotMatchId(UUID participantId, String riotMatchId);

    java.util.Optional<ChallengeParticipantMatch> findByParticipantIdAndRiotMatchId(
            UUID participantId,
            String riotMatchId
    );

    @Query("""
            SELECT rpm
            FROM ChallengeParticipantMatch rpm
            WHERE rpm.participantId = :participantId
              AND rpm.championId IS NULL
            ORDER BY rpm.riotMatchId
            """)
    List<ChallengeParticipantMatch> findMissingChampionIdByParticipantId(
            @Param("participantId") UUID participantId,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            SELECT rpm
            FROM ChallengeParticipantMatch rpm
            WHERE rpm.championId IS NULL
            ORDER BY rpm.riotMatchId
            """)
    List<ChallengeParticipantMatch> findAllMissingChampionId(org.springframework.data.domain.Pageable pageable);

    long countByChampionIdIsNull();

    @Query("""
            SELECT COUNT(rpm)
            FROM ChallengeParticipantMatch rpm
            WHERE rpm.participantId = :participantId
              AND rpm.championId IS NULL
            """)
    long countMissingChampionIdByParticipantId(@Param("participantId") UUID participantId);

    @Query("""
            SELECT rpm.riotMatchId AS matchId, rpm.win AS win
            FROM ChallengeParticipantMatch rpm
            WHERE rpm.participantId = :participantId
            """)
    List<ParticipantMatchOutcome> findOutcomesByParticipantId(@Param("participantId") UUID participantId);

    @Query("""
            SELECT rpm.riotMatchId AS matchId,
                   rpm.win AS win,
                   rm.gameStart AS gameStart
            FROM ChallengeParticipantMatch rpm, RiotMatch rm, Challenge c
            WHERE rpm.participantId = :participantId
              AND rpm.challengeId = :challengeId
              AND c.id = :challengeId
              AND rm.riotMatchId = rpm.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            ORDER BY rm.gameStart DESC
            """)
    List<ParticipantMatchOutcomeInWindow> findOutcomesInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            SELECT rpm
            FROM ChallengeParticipantMatch rpm, RiotMatch rm, Challenge c
            WHERE rpm.participantId = :participantId
              AND rpm.challengeId = :challengeId
              AND c.id = :challengeId
              AND rm.riotMatchId = rpm.riotMatchId
              AND (rm.gameStart < c.startAt OR (c.endAt IS NOT NULL AND rm.gameStart >= c.endAt))
            """)
    List<ChallengeParticipantMatch> findOutsideChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    interface ParticipantMatchOutcome {
        String getMatchId();

        boolean isWin();
    }

    interface ParticipantMatchOutcomeInWindow {
        String getMatchId();

        boolean isWin();

        java.time.Instant getGameStart();
    }

    @Query("""
            SELECT COUNT(rpm)
            FROM ChallengeParticipantMatch rpm, RiotMatch rm, Challenge c
            WHERE rpm.participantId = :participantId
              AND rpm.challengeId = :challengeId
              AND c.id = :challengeId
              AND rm.riotMatchId = rpm.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
              AND rpm.win = true
            """)
    long countWinsInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            SELECT COUNT(rpm)
            FROM ChallengeParticipantMatch rpm, RiotMatch rm, Challenge c
            WHERE rpm.participantId = :participantId
              AND rpm.challengeId = :challengeId
              AND c.id = :challengeId
              AND rm.riotMatchId = rpm.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
              AND rpm.win = false
            """)
    long countLossesInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            SELECT COUNT(rpm)
            FROM ChallengeParticipantMatch rpm
            WHERE rpm.participantId = :participantId AND rpm.win = true
            """)
    long countWinsByParticipantId(@Param("participantId") UUID participantId);

    @Query("""
            SELECT COUNT(rpm)
            FROM ChallengeParticipantMatch rpm
            WHERE rpm.participantId = :participantId AND rpm.win = false
            """)
    long countLossesByParticipantId(@Param("participantId") UUID participantId);

    @Query("""
            SELECT rpm.riotMatchId
            FROM ChallengeParticipantMatch rpm
            WHERE rpm.participantId = :participantId
            """)
    List<String> findMatchIdsByParticipantId(@Param("participantId") UUID participantId);

    @Query("""
            SELECT rpm.riotMatchId AS matchId,
                   rpm.win AS win,
                   rpm.championId AS championId,
                   rm.gameStart AS gameStart
            FROM ChallengeParticipantMatch rpm, RiotMatch rm, Challenge r
            WHERE rpm.participantId = :participantId
              AND rpm.challengeId = :challengeId
              AND r.id = :challengeId
              AND rm.riotMatchId = rpm.riotMatchId
              AND rm.gameStart >= r.startAt
              AND (r.endAt IS NULL OR rm.gameStart < r.endAt)
            ORDER BY rm.gameStart DESC
            """)
    List<ParticipantMatchHistoryRow> findHistoryByParticipantIdAndChallengeId(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            SELECT rpm1.riotMatchId AS matchId,
                   rpm1.win AS win,
                   rpm1.championId AS player1ChampionId,
                   rpm2.championId AS player2ChampionId,
                   rm.gameStart AS gameStart
            FROM ChallengeParticipantMatch rpm1, ChallengeParticipantMatch rpm2, RiotMatch rm, Challenge r
            WHERE rpm1.participantId = :player1Id
              AND rpm2.participantId = :player2Id
              AND rpm1.challengeId = :challengeId
              AND rpm2.challengeId = :challengeId
              AND rpm1.riotMatchId = rpm2.riotMatchId
              AND r.id = :challengeId
              AND rm.riotMatchId = rpm1.riotMatchId
              AND rm.gameStart >= r.startAt
              AND (r.endAt IS NULL OR rm.gameStart < r.endAt)
              AND rpm1.riotMatchId IN :matchIds
            ORDER BY rm.gameStart DESC
            """)
    List<DuoMatchHistoryRow> findDuoHistoryByParticipantIdsAndChallengeId(
            @Param("player1Id") UUID player1Id,
            @Param("player2Id") UUID player2Id,
            @Param("challengeId") UUID challengeId,
            @Param("matchIds") Set<String> matchIds
    );

    @Query("""
            SELECT rpm.riotMatchId
            FROM ChallengeParticipantMatch rpm, RiotMatch rm, Challenge r
            WHERE rpm.participantId = :participantId
              AND rpm.challengeId = :challengeId
              AND r.id = :challengeId
              AND rm.riotMatchId = rpm.riotMatchId
              AND rm.gameStart >= r.startAt
              AND (r.endAt IS NULL OR rm.gameStart < r.endAt)
            """)
    List<String> findMatchIdsInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            SELECT rpm.riotMatchId
            FROM ChallengeParticipantMatch rpm, RiotMatch rm, Challenge r
            WHERE rpm.challengeId = :challengeId
              AND r.id = :challengeId
              AND rm.riotMatchId = rpm.riotMatchId
              AND rpm.riotMatchId IN :matchIds
              AND rm.gameStart >= r.startAt
              AND (r.endAt IS NULL OR rm.gameStart < r.endAt)
            """)
    List<String> findMatchIdsInChallengeWindowForMatches(
            @Param("challengeId") UUID challengeId,
            @Param("matchIds") Set<String> matchIds
    );

    interface ParticipantMatchHistoryRow {
        String getMatchId();

        boolean isWin();

        Integer getChampionId();

        java.time.Instant getGameStart();
    }

    interface DuoMatchHistoryRow {
        String getMatchId();

        boolean isWin();

        Integer getPlayer1ChampionId();

        Integer getPlayer2ChampionId();

        java.time.Instant getGameStart();
    }

    @Query("""
            SELECT COUNT(rpm)
            FROM ChallengeParticipantMatch rpm
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
            FROM ChallengeParticipantMatch rpm
            WHERE rpm.participantId = :participantId
              AND rpm.riotMatchId IN :matchIds
              AND rpm.win = false
            """)
    long countLossesByParticipantIdAndMatchIds(
            @Param("participantId") UUID participantId,
            @Param("matchIds") List<String> matchIds
    );
}
