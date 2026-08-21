package com.riftchallenge.leaderboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Latest known ranked solo/duo rank for a linked account, independent of any challenge.
 * One row per PUUID, upserted on every leaderboard sync.
 */
@Entity
@Table(name = "leaderboard_account_rank")
public class LeaderboardAccountRank {

    @Id
    @Column(name = "riot_puuid", length = 78)
    private String riotPuuid;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(nullable = false, length = 16)
    private String tier;

    @Column(name = "rank_division", length = 4)
    private String rankDivision;

    @Column(name = "league_points", nullable = false)
    private int leaguePoints;

    protected LeaderboardAccountRank() {
    }

    public static LeaderboardAccountRank create(
            String riotPuuid,
            Instant capturedAt,
            String tier,
            String rankDivision,
            int leaguePoints
    ) {
        LeaderboardAccountRank rank = new LeaderboardAccountRank();
        rank.riotPuuid = riotPuuid;
        rank.capturedAt = capturedAt;
        rank.tier = tier;
        rank.rankDivision = rankDivision;
        rank.leaguePoints = leaguePoints;
        return rank;
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

    public void update(Instant capturedAt, String tier, String rankDivision, int leaguePoints) {
        this.capturedAt = capturedAt;
        this.tier = tier;
        this.rankDivision = rankDivision;
        this.leaguePoints = leaguePoints;
    }
}
