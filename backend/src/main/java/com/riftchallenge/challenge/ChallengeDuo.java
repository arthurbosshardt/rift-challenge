package com.riftchallenge.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenge_duo")
public class ChallengeDuo {

    @Id
    private UUID id;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChallengeDuo() {
    }

    public static ChallengeDuo create(UUID challengeId) {
        ChallengeDuo duo = new ChallengeDuo();
        duo.id = UUID.randomUUID();
        duo.challengeId = challengeId;
        return duo;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getChallengeId() {
        return challengeId;
    }
}
