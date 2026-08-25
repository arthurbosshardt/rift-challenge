package com.riftchallenge.leaderboard;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaderboardAccountMatchRepository extends JpaRepository<LeaderboardAccountMatch, java.util.UUID> {

    long countByRiotPuuid(String riotPuuid);

    boolean existsByRiotPuuidAndRiotMatchId(String riotPuuid, String riotMatchId);

    java.util.Optional<LeaderboardAccountMatch> findByRiotPuuidAndRiotMatchId(String riotPuuid, String riotMatchId);

    @Query("""
            SELECT COUNT(lam)
            FROM LeaderboardAccountMatch lam, RiotMatch rm
            WHERE lam.riotPuuid = :riotPuuid
              AND rm.riotMatchId = lam.riotMatchId
              AND rm.gameStart >= :since
            """)
    long countSeasonMatchesSince(
            @Param("riotPuuid") String riotPuuid,
            @Param("since") Instant since
    );

    @Query("""
            SELECT lam.riotMatchId AS matchId,
                   lam.win AS win,
                   lam.championId AS championId,
                   lam.championName AS championName,
                   lam.kills AS kills,
                   lam.deaths AS deaths,
                   lam.assists AS assists,
                   lam.cs AS cs,
                   lam.gameDurationSeconds AS gameDurationSeconds,
                   rm.gameStart AS gameStart
            FROM LeaderboardAccountMatch lam, RiotMatch rm
            WHERE lam.riotPuuid = :riotPuuid
              AND rm.riotMatchId = lam.riotMatchId
              AND rm.gameStart >= :since
            ORDER BY rm.gameStart DESC
            """)
    List<SeasonActivityRow> findSeasonActivitySince(
            @Param("riotPuuid") String riotPuuid,
            @Param("since") Instant since
    );

    @Query("""
            SELECT lam.riotMatchId
            FROM LeaderboardAccountMatch lam, RiotMatch rm
            WHERE lam.riotPuuid = :riotPuuid
              AND rm.riotMatchId = lam.riotMatchId
              AND rm.gameStart >= :since
              AND lam.kills IS NULL
            ORDER BY rm.gameStart DESC
            """)
    List<String> findMatchIdsMissingCombatStatsSince(
            @Param("riotPuuid") String riotPuuid,
            @Param("since") Instant since,
            Pageable pageable
    );

    @Query("""
            SELECT lam.riotMatchId AS matchId,
                   lam.win AS win,
                   lam.championId AS championId,
                   rm.gameStart AS gameStart
            FROM LeaderboardAccountMatch lam, RiotMatch rm
            WHERE lam.riotPuuid = :riotPuuid
              AND rm.riotMatchId = lam.riotMatchId
              AND rm.gameStart >= :since
            ORDER BY rm.gameStart ASC
            """)
    List<AccountMatchHistoryRow> findHistorySince(
            @Param("riotPuuid") String riotPuuid,
            @Param("since") Instant since
    );

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

    interface AccountMatchHistoryRow {
        String getMatchId();

        boolean isWin();

        Integer getChampionId();

        Instant getGameStart();
    }

    /**
     * Ranks a player against every other tracked account that has played the same champion this
     * season, by a composite performance score (win rate 45% + KDA 35% + CS/min 20%). Native SQL
     * because Hibernate/JPQL doesn't support window functions. The score expression is duplicated
     * in ORDER BY since a native-query SELECT alias can't reliably be referenced there.
     */
    @Query(value = """
            WITH per_player_champion AS (
              SELECT lam.riot_puuid AS puuid, lam.champion_id AS champion_id, COUNT(*) AS games,
                     SUM(CASE WHEN lam.win THEN 1 ELSE 0 END) AS wins,
                     SUM(lam.kills) AS kills, SUM(lam.deaths) AS deaths, SUM(lam.assists) AS assists,
                     SUM(lam.cs) AS cs, SUM(lam.game_duration_seconds) AS duration_seconds
              FROM leaderboard_account_match lam
              JOIN riot_match rm ON rm.riot_match_id = lam.riot_match_id
              WHERE rm.game_start >= :since AND lam.champion_id IS NOT NULL AND lam.kills IS NOT NULL
              GROUP BY lam.riot_puuid, lam.champion_id
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
            @Param("minGames") int minGames
    );

    interface ChampionRankRow {
        Integer getChampionId();

        Integer getRank();

        Integer getPoolSize();

        Integer getGames();
    }
}
