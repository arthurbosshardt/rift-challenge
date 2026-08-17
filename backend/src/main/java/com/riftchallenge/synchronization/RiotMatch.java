package com.riftchallenge.synchronization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "riot_match")
public class RiotMatch {

    @Id
    private UUID id;

    @Column(name = "riot_match_id", nullable = false, unique = true, length = 32)
    private String riotMatchId;

    @Column(name = "queue_id", nullable = false)
    private int queueId;

    @Column(name = "game_start", nullable = false)
    private Instant gameStart;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RiotMatch() {
    }

    public static RiotMatch create(String riotMatchId, int queueId, Instant gameStart) {
        RiotMatch match = new RiotMatch();
        match.id = UUID.randomUUID();
        match.riotMatchId = riotMatchId;
        match.queueId = queueId;
        match.gameStart = gameStart;
        return match;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public String getRiotMatchId() {
        return riotMatchId;
    }

    public Instant getGameStart() {
        return gameStart;
    }
}
