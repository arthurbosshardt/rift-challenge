package com.riftchallenge.challenge;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "challenge_list_cache_state")
public class ChallengeListCacheState {

    public static final short SINGLETON_ID = 1;

    @Id
    private short id = SINGLETON_ID;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    protected ChallengeListCacheState() {
    }

    public ChallengeListCacheState(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }

    public short getId() {
        return id;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public void updateGeneratedAt(Instant generatedAt) {
        this.generatedAt = generatedAt;
    }
}
