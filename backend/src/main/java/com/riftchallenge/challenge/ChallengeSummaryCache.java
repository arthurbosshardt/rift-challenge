package com.riftchallenge.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenge_summary_cache")
public class ChallengeSummaryCache {

    @Id
    @Column(name = "challenge_id")
    private UUID challengeId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ChallengeSummaryCache() {
    }

    public ChallengeSummaryCache(UUID challengeId, Instant startAt, String payload, Instant generatedAt) {
        this.challengeId = challengeId;
        this.startAt = startAt;
        this.payload = payload;
        this.generatedAt = generatedAt;
    }

    public UUID getChallengeId() {
        return challengeId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }
}
