package com.riftchallenge.leaderboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * A ranked solo/duo match counted toward the global leaderboard, independent of any challenge.
 * Keyed by PUUID rather than a challenge participation — see {@link LeaderboardAccountSyncService}.
 */
@Entity
@Table(name = "leaderboard_account_match")
public class LeaderboardAccountMatch {

    @Id
    private UUID id;

    @Column(name = "riot_puuid", nullable = false, length = 78)
    private String riotPuuid;

    @Column(name = "riot_match_id", nullable = false, length = 32)
    private String riotMatchId;

    @Column(nullable = false)
    private boolean win;

    @Column(name = "champion_id")
    private Integer championId;

    protected LeaderboardAccountMatch() {
    }

    public static LeaderboardAccountMatch create(String riotPuuid, String riotMatchId, boolean win, Integer championId) {
        LeaderboardAccountMatch match = new LeaderboardAccountMatch();
        match.id = UUID.randomUUID();
        match.riotPuuid = riotPuuid;
        match.riotMatchId = riotMatchId;
        match.win = win;
        match.championId = championId;
        return match;
    }

    public String getRiotPuuid() {
        return riotPuuid;
    }

    public String getRiotMatchId() {
        return riotMatchId;
    }

    public boolean isWin() {
        return win;
    }

    public Integer getChampionId() {
        return championId;
    }
}
