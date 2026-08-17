package com.riftrace.race;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "race_duo")
public class RaceDuo {

    @Id
    private UUID id;

    @Column(name = "race_id", nullable = false)
    private UUID raceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RaceDuo() {
    }

    public static RaceDuo create(UUID raceId) {
        RaceDuo duo = new RaceDuo();
        duo.id = UUID.randomUUID();
        duo.raceId = raceId;
        return duo;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRaceId() {
        return raceId;
    }
}
