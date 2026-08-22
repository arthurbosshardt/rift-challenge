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

    @Column(name = "champion_name", length = 32)
    private String championName;

    @Column
    private Integer kills;

    @Column
    private Integer deaths;

    @Column
    private Integer assists;

    @Column
    private Integer cs;

    @Column(name = "game_duration_seconds")
    private Long gameDurationSeconds;

    protected LeaderboardAccountMatch() {
    }

    public static LeaderboardAccountMatch create(
            String riotPuuid,
            String riotMatchId,
            boolean win,
            Integer championId,
            String championName,
            int kills,
            int deaths,
            int assists,
            int cs,
            long gameDurationSeconds
    ) {
        LeaderboardAccountMatch match = new LeaderboardAccountMatch();
        match.id = UUID.randomUUID();
        match.riotPuuid = riotPuuid;
        match.riotMatchId = riotMatchId;
        match.win = win;
        match.championId = championId;
        match.championName = championName;
        match.kills = kills;
        match.deaths = deaths;
        match.assists = assists;
        match.cs = cs;
        match.gameDurationSeconds = gameDurationSeconds;
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

    public String getChampionName() {
        return championName;
    }

    public Integer getKills() {
        return kills;
    }

    public Integer getDeaths() {
        return deaths;
    }

    public Integer getAssists() {
        return assists;
    }

    public Integer getCs() {
        return cs;
    }

    public Long getGameDurationSeconds() {
        return gameDurationSeconds;
    }

    public boolean hasCombatStats() {
        return kills != null && deaths != null && assists != null && cs != null && gameDurationSeconds != null;
    }

    public void backfillCombatStats(
            String championName,
            int kills,
            int deaths,
            int assists,
            int cs,
            long gameDurationSeconds
    ) {
        if (this.championName == null && championName != null && !championName.isBlank()) {
            this.championName = championName;
        }
        this.kills = kills;
        this.deaths = deaths;
        this.assists = assists;
        this.cs = cs;
        this.gameDurationSeconds = gameDurationSeconds;
    }
}
