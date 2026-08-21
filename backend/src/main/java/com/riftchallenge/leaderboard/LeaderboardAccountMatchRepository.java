package com.riftchallenge.leaderboard;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeaderboardAccountMatchRepository extends JpaRepository<LeaderboardAccountMatch, java.util.UUID> {

    long countByRiotPuuid(String riotPuuid);

    boolean existsByRiotPuuidAndRiotMatchId(String riotPuuid, String riotMatchId);

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

    interface AccountMatchHistoryRow {
        String getMatchId();

        boolean isWin();

        Integer getChampionId();

        Instant getGameStart();
    }
}
