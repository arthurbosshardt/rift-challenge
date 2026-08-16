package com.riftrace.race;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "race_refresh")
public class RaceRefresh {

    @Id
    private UUID id;

    @Column(name = "race_id", nullable = false, unique = true)
    private UUID raceId;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;

    protected RaceRefresh() {
    }

    public static RaceRefresh create(UUID raceId, Instant refreshedAt) {
        RaceRefresh refresh = new RaceRefresh();
        refresh.id = UUID.randomUUID();
        refresh.raceId = raceId;
        refresh.refreshedAt = refreshedAt;
        return refresh;
    }

    @PrePersist
    void onCreate() {
        if (refreshedAt == null) {
            refreshedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getRaceId() {
        return raceId;
    }

    public Instant getRefreshedAt() {
        return refreshedAt;
    }

    public void updateRefreshedAt(Instant refreshedAt) {
        this.refreshedAt = refreshedAt;
    }
}
