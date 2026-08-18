package com.riftchallenge.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
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

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "share_slug", nullable = false, unique = true)
    private UUID shareSlug;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "data_synced_at")
    private Instant dataSyncedAt;

    protected Challenge() {
    }

    public static Challenge create(UUID ownerId, String name, ChallengeType type, Instant startAt, boolean isPublic) {
        return create(ownerId, name, type, startAt, null, isPublic);
    }

    public static Challenge create(
            UUID ownerId,
            String name,
            ChallengeType type,
            Instant startAt,
            Instant endAt,
            boolean isPublic
    ) {
        Challenge challenge = new Challenge();
        challenge.id = UUID.randomUUID();
        challenge.ownerId = ownerId;
        challenge.name = name;
        challenge.type = type;
        challenge.startAt = startAt;
        challenge.endAt = endAt;
        challenge.isPublic = isPublic;
        challenge.shareSlug = UUID.randomUUID();
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

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public UUID getShareSlug() {
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

    public void updateVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void updateEndAt(Instant endAt) {
        this.endAt = endAt;
    }

    public void updateStartAt(Instant startAt) {
        this.startAt = startAt;
    }

    public void updateName(String name) {
        this.name = name;
    }
}
