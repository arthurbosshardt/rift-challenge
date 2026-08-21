package com.riftchallenge.leaderboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "leaderboard_cache")
public class LeaderboardCache {

    public static final short SINGLETON_ID = 1;

    @Id
    private short id = SINGLETON_ID;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected LeaderboardCache() {
    }

    public LeaderboardCache(String payload, Instant generatedAt) {
        this.payload = payload;
        this.generatedAt = generatedAt;
    }

    public short getId() {
        return id;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
