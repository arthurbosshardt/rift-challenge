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
}
