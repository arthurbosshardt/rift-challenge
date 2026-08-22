package com.riftchallenge.account;

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
@Table(name = "user_riot_account")
public class UserRiotAccount {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "riot_puuid", nullable = false, length = 78)
    private String riotPuuid;

    @Column(name = "riot_game_name", nullable = false, length = 16)
    private String riotGameName;

    @Column(name = "riot_tag_line", nullable = false, length = 5)
    private String riotTagLine;

    @Column(name = "profile_icon_id")
    private Integer profileIconId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activity_season_history_exhausted", nullable = false)
    private boolean activitySeasonHistoryExhausted;

    protected UserRiotAccount() {
    }

    public static UserRiotAccount create(UUID userId, RiotAccountDto account) {
        return create(userId, account, null);
    }

    public static UserRiotAccount create(UUID userId, RiotAccountDto account, Integer profileIconId) {
        UserRiotAccount linked = new UserRiotAccount();
        linked.id = UUID.randomUUID();
        linked.userId = userId;
        linked.riotPuuid = account.puuid();
        linked.riotGameName = account.gameName();
        linked.riotTagLine = account.tagLine();
        linked.profileIconId = profileIconId;
        linked.activitySeasonHistoryExhausted = false;
        return linked;
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

    public UUID getUserId() {
        return userId;
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

    public void updateProfileIconId(Integer profileIconId) {
        this.profileIconId = profileIconId;
    }

    public boolean isActivitySeasonHistoryExhausted() {
        return activitySeasonHistoryExhausted;
    }

    public void markActivitySeasonHistoryExhausted() {
        this.activitySeasonHistoryExhausted = true;
    }

    public void clearActivitySeasonHistoryExhausted() {
        this.activitySeasonHistoryExhausted = false;
    }
}
