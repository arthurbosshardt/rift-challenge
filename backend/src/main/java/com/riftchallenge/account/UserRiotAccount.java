package com.riftchallenge.account;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "riot_account_id", nullable = false)
    private RiotAccount riotAccount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserRiotAccount() {
    }

    public static UserRiotAccount create(UUID userId, RiotAccount riotAccount) {
        UserRiotAccount linked = new UserRiotAccount();
        linked.id = UUID.randomUUID();
        linked.userId = userId;
        linked.riotAccount = riotAccount;
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

    public RiotAccount getRiotAccount() {
        return riotAccount;
    }

    public String getRiotPuuid() {
        return riotAccount.getRiotPuuid();
    }

    public String getRiotGameName() {
        return riotAccount.getRiotGameName();
    }

    public String getRiotTagLine() {
        return riotAccount.getRiotTagLine();
    }

    public Integer getProfileIconId() {
        return riotAccount.getProfileIconId();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void updateProfileIconId(Integer profileIconId) {
        riotAccount.updateProfileIconId(profileIconId);
    }

    public boolean isActivitySeasonHistoryExhausted() {
        return riotAccount.isActivitySeasonHistoryExhausted();
    }

    public void markActivitySeasonHistoryExhausted() {
        riotAccount.markActivitySeasonHistoryExhausted();
    }

    public void clearActivitySeasonHistoryExhausted() {
        riotAccount.clearActivitySeasonHistoryExhausted();
    }
}
