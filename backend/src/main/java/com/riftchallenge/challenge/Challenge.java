package com.riftchallenge.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import com.riftchallenge.riot.ChallengeRegion;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenge")
public class Challenge {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChallengeType type;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at")
    private Instant endAt;

    @Column(name = "max_games")
    private Integer maxGames;

    @Column(name = "share_slug", nullable = false, unique = true, length = 120)
    private String shareSlug;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "data_synced_at")
    private Instant dataSyncedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChallengeRegion region;

    protected Challenge() {
    }

    public static Challenge create(UUID ownerId, String name, ChallengeType type, Instant startAt) {
        return create(ownerId, name, type, startAt, null, null, ChallengeRegion.EUW);
    }

    public static Challenge create(
            UUID ownerId,
            String name,
            ChallengeType type,
            Instant startAt,
            Instant endAt
    ) {
        return create(ownerId, name, type, startAt, endAt, null, ChallengeRegion.EUW);
    }

    public static Challenge create(
            UUID ownerId,
            String name,
            ChallengeType type,
            Instant startAt,
            Instant endAt,
            Integer maxGames
    ) {
        return create(ownerId, name, type, startAt, endAt, maxGames, ChallengeRegion.EUW);
    }

    public static Challenge create(
            UUID ownerId,
            String name,
            ChallengeType type,
            Instant startAt,
            Instant endAt,
            Integer maxGames,
            ChallengeRegion region
    ) {
        Challenge challenge = new Challenge();
        challenge.id = UUID.randomUUID();
        challenge.ownerId = ownerId;
        challenge.name = name;
        challenge.type = type;
        challenge.startAt = startAt;
        challenge.endAt = endAt;
        challenge.maxGames = maxGames;
        challenge.shareSlug = challenge.id.toString();
        // Fixed at creation, never updated afterward.
        challenge.region = region;
        return challenge;
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

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getName() {
        return name;
    }

    public ChallengeType getType() {
        return type;
    }

    public ChallengeRegion getRegion() {
        return region;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Integer getMaxGames() {
        return maxGames;
    }

    public String getShareSlug() {
        return shareSlug;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDataSyncedAt() {
        return dataSyncedAt;
    }

    public void updateDataSyncedAt(Instant dataSyncedAt) {
        this.dataSyncedAt = dataSyncedAt;
    }

    public void updateEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public void updateMaxGames(Integer maxGames) {
        this.maxGames = maxGames;
    }

    public void updateStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public void updateName(String name) {
        this.name = name;
    }
}
