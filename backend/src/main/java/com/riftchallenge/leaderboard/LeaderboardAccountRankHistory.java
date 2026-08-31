package com.riftchallenge.leaderboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One timestamped rank snapshot for a linked account, insert-only — unlike
 * {@link LeaderboardAccountRank}, which only ever holds the latest snapshot, this accumulates a
 * real history so LP-gained figures can be computed as an exact score delta between two real
 * snapshots (see {@link com.riftchallenge.riot.RankScoreConverter#lpGained}) instead of the
 * win/loss-sequence heuristic.
 */
@Entity
@Table(name = "leaderboard_account_rank_history")
public class LeaderboardAccountRankHistory {

    @Id
    private UUID id;

    @Column(name = "riot_puuid", nullable = false, length = 78)
    private String riotPuuid;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(nullable = false, length = 16)
    private String tier;

    @Column(name = "rank_division", length = 4)
    private String rankDivision;

    @Column(name = "league_points", nullable = false)
    private int leaguePoints;

    protected LeaderboardAccountRankHistory() {
    }

    public static LeaderboardAccountRankHistory create(
            String riotPuuid,
            Instant capturedAt,
            String tier,
            String rankDivision,
            int leaguePoints
    ) {
        LeaderboardAccountRankHistory history = new LeaderboardAccountRankHistory();
        history.id = UUID.randomUUID();
        history.riotPuuid = riotPuuid;
        history.capturedAt = capturedAt;
        history.tier = tier;
        history.rankDivision = rankDivision;
        history.leaguePoints = leaguePoints;
        return history;
    }

    public UUID getId() {
        return id;
    }

    public String getRiotPuuid() {
        return riotPuuid;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public String getTier() {
        return tier;
    }

    public String getRankDivision() {
        return rankDivision;
    }

    public int getLeaguePoints() {
        return leaguePoints;
    }
}
