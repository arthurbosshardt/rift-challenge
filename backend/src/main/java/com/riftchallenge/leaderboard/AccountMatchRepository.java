package com.riftchallenge.leaderboard;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountMatchRepository extends JpaRepository<AccountMatch, UUID> {

    long countByRiotPuuid(String riotPuuid);

    boolean existsByRiotPuuidAndRiotMatchId(String riotPuuid, String riotMatchId);

    Optional<AccountMatch> findByRiotPuuidAndRiotMatchId(String riotPuuid, String riotMatchId);

    @Query("""
            SELECT COUNT(am)
            FROM AccountMatch am, RiotMatch rm
            WHERE am.riotPuuid = :riotPuuid
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= :since
            """)
    long countSeasonMatchesSince(
            @Param("riotPuuid") String riotPuuid,
            @Param("since") Instant since
    );

    @Query("""
            SELECT am.riotMatchId AS matchId,
                   am.win AS win,
                   am.championId AS championId,
                   am.championName AS championName,
                   am.kills AS kills,
                   am.deaths AS deaths,
                   am.assists AS assists,
                   am.cs AS cs,
                   am.gameDurationSeconds AS gameDurationSeconds,
                   rm.gameStart AS gameStart
            FROM AccountMatch am, RiotMatch rm
            WHERE am.riotPuuid = :riotPuuid
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= :since
            ORDER BY rm.gameStart DESC
            """)
    List<SeasonActivityRow> findSeasonActivitySince(
            @Param("riotPuuid") String riotPuuid,
            @Param("since") Instant since
    );

    @Query("""
            SELECT am.riotMatchId
            FROM AccountMatch am, RiotMatch rm
            WHERE am.riotPuuid = :riotPuuid
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= :since
              AND am.kills IS NULL
            ORDER BY rm.gameStart DESC
            """)
    List<String> findMatchIdsMissingCombatStatsSince(
            @Param("riotPuuid") String riotPuuid,
            @Param("since") Instant since,
            Pageable pageable
    );

    /**
     * Fetches season history for every given account in one query instead of one query per
     * account (used by the leaderboard computation, which otherwise issues N queries for N
     * tracked accounts on every scheduled recompute).
     */
    @Query("""
            SELECT am.riotPuuid AS riotPuuid,
                   am.riotMatchId AS matchId,
                   am.win AS win,
                   am.championId AS championId,
                   rm.gameStart AS gameStart
            FROM AccountMatch am, RiotMatch rm
            WHERE am.riotPuuid IN :riotPuuids
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= :since
            ORDER BY rm.gameStart ASC
            """)
    List<AccountMatchHistoryRowForPuuid> findHistorySinceForPuuids(
            @Param("riotPuuids") Set<String> riotPuuids,
            @Param("since") Instant since
    );

    interface AccountMatchHistoryRowForPuuid {
        String getRiotPuuid();

        String getMatchId();

        boolean isWin();

        Integer getChampionId();

        Instant getGameStart();
    }

    interface SeasonActivityRow {
        String getMatchId();

        boolean isWin();

        Integer getChampionId();

        String getChampionName();

        Integer getKills();

        Integer getDeaths();

        Integer getAssists();

        Integer getCs();

        Long getGameDurationSeconds();

        Instant getGameStart();
    }


    /**
     * Ranks a player against every other tracked account that has played the same champion this
     * season, by a composite performance score (win rate 45% + KDA 35% + CS/min 20%). Native SQL
     * because Hibernate/JPQL doesn't support window functions. The score expression is duplicated
     * in ORDER BY since a native-query SELECT alias can't reliably be referenced there.
     *
     * <p>{@code championIds} restricts the whole computation (including the RANK()/pool_size
     * window, via the CTE's WHERE clause) to the caller's own played champions instead of ranking
     * every champion in the pool — the caller only ever reads rows for those champions anyway, and
     * this query previously computed ranks for the full season's worth of champions on every
     * profile view. Must not be called with an empty collection: {@code column IN ()} is invalid
     * SQL, so callers should skip the call entirely when they have no champions to rank.
     */
    @Query(value = """
            WITH per_player_champion AS (
              SELECT am.riot_puuid AS puuid, am.champion_id AS champion_id, COUNT(*) AS games,
                     SUM(CASE WHEN am.win THEN 1 ELSE 0 END) AS wins,
                     SUM(am.kills) AS kills, SUM(am.deaths) AS deaths, SUM(am.assists) AS assists,
                     SUM(am.cs) AS cs, SUM(am.game_duration_seconds) AS duration_seconds
              FROM account_match am
              JOIN riot_match rm ON rm.riot_match_id = am.riot_match_id
              WHERE rm.game_start >= :since AND am.champion_id IN (:championIds) AND am.kills IS NOT NULL
              GROUP BY am.riot_puuid, am.champion_id
              HAVING COUNT(*) >= :minGames
            ),
            scored AS (
              SELECT puuid, champion_id, games,
                     RANK() OVER (
                       PARTITION BY champion_id
                       ORDER BY 45.0 * (wins::float / games)
                                + 35.0 * (LEAST((kills + assists)::float / GREATEST(deaths, 1), 6) / 6)
                                + 20.0 * (LEAST(cs::float / GREATEST(duration_seconds / 60.0, 1), 10) / 10) DESC
                     ) AS rnk,
                     COUNT(*) OVER (PARTITION BY champion_id) AS pool_size
              FROM per_player_champion
            )
            SELECT champion_id AS championId, rnk AS rank, pool_size AS poolSize, games AS games
            FROM scored WHERE puuid = :puuid ORDER BY rnk ASC
            """, nativeQuery = true)
    List<ChampionRankRow> findChampionRanks(
            @Param("puuid") String puuid,
            @Param("since") Instant since,
            @Param("minGames") int minGames,
            @Param("championIds") Set<Integer> championIds
    );

    interface ChampionRankRow {
        Integer getChampionId();

        Integer getRank();

        Integer getPoolSize();

        Integer getGames();
    }

    // ---------------------------------------------------------------------------------------
    // Challenge-scoped queries: challenges have no persisted junction table of their own, so
    // every method below resolves a participant's riotPuuid via ChallengeParticipant and filters
    // matches to the challenge's own date window ([startAt, endAt)) live, at query time.
    // ---------------------------------------------------------------------------------------

    @Query("""
            SELECT am.riotMatchId AS matchId,
                   am.win AS win,
                   rm.gameStart AS gameStart
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id = :participantId
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            ORDER BY rm.gameStart DESC
            """)
    List<ParticipantMatchOutcomeInWindow> findOutcomesInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    interface ParticipantMatchOutcomeInWindow {
        String getMatchId();

        boolean isWin();

        Instant getGameStart();
    }

    @Query("""
            SELECT COUNT(am)
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id = :participantId
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
              AND am.win = true
            """)
    long countWinsInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            SELECT COUNT(am)
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id = :participantId
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
              AND am.win = false
            """)
    long countLossesInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            SELECT cp.id AS participantId,
                   SUM(CASE WHEN am.win = true THEN 1L ELSE 0L END) AS wins,
                   SUM(CASE WHEN am.win = false THEN 1L ELSE 0L END) AS losses
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id IN :participantIds
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            GROUP BY cp.id
            """)
    List<ParticipantWinLossCount> countWinsAndLossesInChallengeWindowForParticipants(
            @Param("participantIds") List<UUID> participantIds,
            @Param("challengeId") UUID challengeId
    );

    interface ParticipantWinLossCount {
        UUID getParticipantId();

        long getWins();

        long getLosses();
    }

    @Query("""
            SELECT cp.id AS participantId,
                   am.riotMatchId AS matchId,
                   am.win AS win,
                   rm.gameStart AS gameStart
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id IN :participantIds
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            """)
    List<ParticipantMatchOutcomeInWindowForParticipant> findOutcomesInChallengeWindowForParticipants(
            @Param("participantIds") List<UUID> participantIds,
            @Param("challengeId") UUID challengeId
    );

    interface ParticipantMatchOutcomeInWindowForParticipant {
        UUID getParticipantId();

        String getMatchId();

        boolean isWin();

        Instant getGameStart();
    }

    @Query("""
            SELECT rm.riotMatchId AS matchId, rm.gameStart AS gameStart
            FROM RiotMatch rm
            WHERE rm.riotMatchId IN :matchIds
            """)
    List<MatchIdAndGameStart> findGameStartsForMatchIds(@Param("matchIds") Set<String> matchIds);

    interface MatchIdAndGameStart {
        String getMatchId();

        Instant getGameStart();
    }

    @Query("""
            SELECT am.riotMatchId AS matchId,
                   am.win AS win,
                   am.championId AS championId,
                   rm.gameStart AS gameStart
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id = :participantId
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            ORDER BY rm.gameStart DESC
            """)
    List<ParticipantMatchHistoryRow> findHistoryByParticipantIdAndChallengeId(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    interface ParticipantMatchHistoryRow {
        String getMatchId();

        boolean isWin();

        Integer getChampionId();

        Instant getGameStart();
    }

    /**
     * Batched form of {@link #findHistoryByParticipantIdAndChallengeId} — fetches match history
     * for every given participant in one query instead of one query per participant (used on the
     * challenge detail page, which otherwise issues N queries for N SOLOQ participants).
     */
    @Query("""
            SELECT cp.id AS participantId,
                   am.riotMatchId AS matchId,
                   am.win AS win,
                   am.championId AS championId,
                   rm.gameStart AS gameStart
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id IN :participantIds
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            ORDER BY rm.gameStart DESC
            """)
    List<ParticipantMatchHistoryRowForParticipant> findHistoryByParticipantIdsAndChallengeId(
            @Param("participantIds") List<UUID> participantIds,
            @Param("challengeId") UUID challengeId
    );

    interface ParticipantMatchHistoryRowForParticipant {
        UUID getParticipantId();

        String getMatchId();

        boolean isWin();

        Integer getChampionId();

        Instant getGameStart();
    }

    @Query("""
            SELECT am1.riotMatchId AS matchId,
                   am1.win AS win,
                   am1.championId AS player1ChampionId,
                   am2.championId AS player2ChampionId,
                   rm.gameStart AS gameStart
            FROM AccountMatch am1, AccountMatch am2, RiotMatch rm, ChallengeParticipant cp1, ChallengeParticipant cp2, Challenge c
            WHERE cp1.id = :player1Id
              AND cp2.id = :player2Id
              AND cp1.riotPuuid = am1.riotPuuid
              AND cp2.riotPuuid = am2.riotPuuid
              AND am1.riotMatchId = am2.riotMatchId
              AND c.id = :challengeId
              AND rm.riotMatchId = am1.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
              AND am1.riotMatchId IN :matchIds
            ORDER BY rm.gameStart DESC
            """)
    List<DuoMatchHistoryRow> findDuoHistoryByParticipantIdsAndChallengeId(
            @Param("player1Id") UUID player1Id,
            @Param("player2Id") UUID player2Id,
            @Param("challengeId") UUID challengeId,
            @Param("matchIds") Set<String> matchIds
    );

    interface DuoMatchHistoryRow {
        String getMatchId();

        boolean isWin();

        Integer getPlayer1ChampionId();

        Integer getPlayer2ChampionId();

        Instant getGameStart();
    }

    @Query("""
            SELECT am.riotMatchId
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id = :participantId
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            """)
    List<String> findMatchIdsInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );

    @Query("""
            SELECT DISTINCT am.riotMatchId
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.challengeId = :challengeId
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND am.riotMatchId IN :matchIds
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            """)
    List<String> findMatchIdsInChallengeWindowForMatches(
            @Param("challengeId") UUID challengeId,
            @Param("matchIds") Set<String> matchIds
    );

    @Query("""
            SELECT COUNT(am)
            FROM AccountMatch am, ChallengeParticipant cp
            WHERE cp.id = :participantId
              AND cp.riotPuuid = am.riotPuuid
              AND am.riotMatchId IN :matchIds
              AND am.win = true
            """)
    long countWinsByParticipantIdAndMatchIds(
            @Param("participantId") UUID participantId,
            @Param("matchIds") List<String> matchIds
    );

    @Query("""
            SELECT COUNT(am)
            FROM AccountMatch am, ChallengeParticipant cp
            WHERE cp.id = :participantId
              AND cp.riotPuuid = am.riotPuuid
              AND am.riotMatchId IN :matchIds
              AND am.win = false
            """)
    long countLossesByParticipantIdAndMatchIds(
            @Param("participantId") UUID participantId,
            @Param("matchIds") List<String> matchIds
    );

    // ---------------------------------------------------------------------------------------
    // Champion-ID backfill (see AccountMatchChampionBackfillService), keyed by riotPuuid since
    // champion data belongs to the account, not to any one challenge's participation link.
    // ---------------------------------------------------------------------------------------

    @Query("""
            SELECT am
            FROM AccountMatch am
            WHERE am.riotPuuid = :riotPuuid
              AND am.championId IS NULL
            ORDER BY am.riotMatchId
            """)
    List<AccountMatch> findMissingChampionIdByRiotPuuid(
            @Param("riotPuuid") String riotPuuid,
            Pageable pageable
    );

    @Query("""
            SELECT am
            FROM AccountMatch am
            WHERE am.championId IS NULL
            ORDER BY am.riotMatchId
            """)
    List<AccountMatch> findAllMissingChampionId(Pageable pageable);

    long countByChampionIdIsNull();

    @Query("""
            SELECT COUNT(am)
            FROM AccountMatch am
            WHERE am.riotPuuid = :riotPuuid
              AND am.championId IS NULL
            """)
    long countMissingChampionIdByRiotPuuid(@Param("riotPuuid") String riotPuuid);

    // ---------------------------------------------------------------------------------------
    // Challenge-window-scoped import budgeting (see ChallengeParticipantSyncService), replacing
    // the old raw per-participant row count now that rows aren't exclusive to one challenge.
    // ---------------------------------------------------------------------------------------

    @Query("""
            SELECT COUNT(am)
            FROM AccountMatch am, RiotMatch rm, ChallengeParticipant cp, Challenge c
            WHERE cp.id = :participantId
              AND cp.riotPuuid = am.riotPuuid
              AND c.id = :challengeId
              AND rm.riotMatchId = am.riotMatchId
              AND rm.gameStart >= c.startAt
              AND (c.endAt IS NULL OR rm.gameStart < c.endAt)
            """)
    long countInChallengeWindow(
            @Param("participantId") UUID participantId,
            @Param("challengeId") UUID challengeId
    );
}
