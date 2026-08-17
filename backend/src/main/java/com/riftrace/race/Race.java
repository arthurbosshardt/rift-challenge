package com.riftrace.race;

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
@Table(name = "race")
public class Race {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RaceType type;

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

    protected Race() {
    }

    public static Race create(UUID ownerId, String name, RaceType type, Instant startAt, boolean isPublic) {
        return create(ownerId, name, type, startAt, null, isPublic);
    }

    public static Race create(
            UUID ownerId,
            String name,
            RaceType type,
            Instant startAt,
            Instant endAt,
            boolean isPublic
    ) {
        Race race = new Race();
        race.id = UUID.randomUUID();
        race.ownerId = ownerId;
        race.name = name;
        race.type = type;
        race.startAt = startAt;
        race.endAt = endAt;
        race.isPublic = isPublic;
        race.shareSlug = UUID.randomUUID();
        return race;
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

    public RaceType getType() {
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
