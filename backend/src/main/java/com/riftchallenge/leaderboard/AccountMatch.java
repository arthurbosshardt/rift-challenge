package com.riftchallenge.leaderboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * One tracked account's participation in one ranked solo/duo match — the single source of truth
 * for both the global leaderboard and every challenge's progress, keyed by {@code riotPuuid} +
 * {@code riotMatchId} regardless of which (if any) challenge the account belongs to. Challenges
 * compute their own progress by querying this table filtered by their own date window / max-games
 * cap, rather than through a per-challenge junction table.
 */
@Entity
@Table(name = "account_match")
public class AccountMatch {

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

    private Integer kills;
    private Integer deaths;
    private Integer assists;
    private Integer cs;

    @Column(name = "game_duration_seconds")
    private Long gameDurationSeconds;

    /**
     * True only for rows migrated in from the old {@code challenge_participant_match} junction
     * table, which tracked only win/loss + champion — never combat stats. Past/ended challenges
     * rely on this to flag that their match data may be less precise than what current syncs
     * capture.
     */
    @Column(nullable = false)
    private boolean historical;

    protected AccountMatch() {
    }

    public static AccountMatch create(
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
        AccountMatch match = new AccountMatch();
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
        match.historical = false;
        return match;
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

    public void updateChampionId(Integer championId) {
        this.championId = championId;
    }

    public UUID getId() {
        return id;
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

    public boolean isHistorical() {
        return historical;
    }
}
