package com.riftchallenge.account;

import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.dto.RiotAccountDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "riot_account")
public class RiotAccount {

    @Id
    private UUID id;

    @Column(name = "riot_puuid", nullable = false, length = 78, unique = true)
    private String riotPuuid;

    @Column(name = "riot_game_name", nullable = false, length = 16)
    private String riotGameName;

    @Column(name = "riot_tag_line", nullable = false, length = 5)
    private String riotTagLine;

    @Column(name = "profile_icon_id")
    private Integer profileIconId;

    @Column(name = "activity_season_history_exhausted", nullable = false)
    private boolean activitySeasonHistoryExhausted;

    @Column(name = "activity_season_history_exhausted_total")
    private Integer activitySeasonHistoryExhaustedTotal;

    @Column(name = "region", length = 8)
    private String region;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RiotAccount() {
    }

    public static RiotAccount create(RiotAccountDto account, Integer profileIconId) {
        RiotAccount riotAccount = new RiotAccount();
        riotAccount.id = UUID.randomUUID();
        riotAccount.riotPuuid = account.puuid();
        riotAccount.riotGameName = account.gameName();
        riotAccount.riotTagLine = account.tagLine();
        riotAccount.profileIconId = profileIconId;
        riotAccount.activitySeasonHistoryExhausted = false;
        return riotAccount;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getRiotPuuid() {
        return riotPuuid;
    }

    public String getRiotGameName() {
        return riotGameName;
    }

    public String getRiotTagLine() {
        return riotTagLine;
    }

    public Integer getProfileIconId() {
        return profileIconId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateIdentity(String gameName, String tagLine, Integer profileIconId) {
        this.riotGameName = gameName;
        this.riotTagLine = tagLine;
        if (profileIconId != null) {
            this.profileIconId = profileIconId;
        }
    }

    public void updateProfileIconId(Integer profileIconId) {
        this.profileIconId = profileIconId;
    }

    public boolean isActivitySeasonHistoryExhausted() {
        return activitySeasonHistoryExhausted;
    }

    /** Season match total (wins + losses) recorded when the exhausted flag was last set, or {@code null}
     *  if unknown (e.g. rows written before this tracking existed). A stale flag whose recorded total
     *  is lower than the current live season total no longer blocks catch-up. */
    public Integer getActivitySeasonHistoryExhaustedTotal() {
        return activitySeasonHistoryExhaustedTotal;
    }

    public void markActivitySeasonHistoryExhausted(int seasonMatchTotal) {
        this.activitySeasonHistoryExhausted = true;
        this.activitySeasonHistoryExhaustedTotal = seasonMatchTotal;
    }

    public void clearActivitySeasonHistoryExhausted() {
        this.activitySeasonHistoryExhausted = false;
        this.activitySeasonHistoryExhaustedTotal = null;
    }

    /** Riot server this account was last confirmed to play on, or {@code null} if not yet detected. */
    public ChallengeRegion getRegion() {
        return region == null ? null : ChallengeRegion.valueOf(region);
    }

    public void assignRegion(ChallengeRegion region) {
        this.region = region.name();
    }
}
