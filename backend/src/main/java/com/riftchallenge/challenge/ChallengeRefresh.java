package com.riftchallenge.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenge_refresh")
public class ChallengeRefresh {

    @Id
    private UUID id;

    @Column(name = "challenge_id", nullable = false, unique = true)
    private UUID challengeId;

    @Column(name = "refreshed_at", nullable = false)
    private Instant refreshedAt;

    protected ChallengeRefresh() {
    }

    public static ChallengeRefresh create(UUID challengeId, Instant refreshedAt) {
        ChallengeRefresh refresh = new ChallengeRefresh();
        refresh.id = UUID.randomUUID();
        refresh.challengeId = challengeId;
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

    public UUID getChallengeId() {
        return challengeId;
    }

    public Instant getRefreshedAt() {
        return refreshedAt;
    }

    public void updateRefreshedAt(Instant refreshedAt) {
        this.refreshedAt = refreshedAt;
    }
}
