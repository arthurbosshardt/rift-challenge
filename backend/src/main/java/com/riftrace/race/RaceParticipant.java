package com.riftrace.race;

import com.riftrace.riot.dto.RiotAccountDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "race_participant")
public class RaceParticipant {

    @Id
    private UUID id;

    @Column(name = "race_id", nullable = false)
    private UUID raceId;

    @Column(name = "riot_puuid", nullable = false, length = 78)
    private String riotPuuid;

    @Column(name = "riot_game_name", nullable = false, length = 16)
    private String riotGameName;

    @Column(name = "riot_tag_line", nullable = false, length = 5)
    private String riotTagLine;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RaceParticipant() {
    }

    public static RaceParticipant create(UUID raceId, RiotAccountDto account) {
        RaceParticipant participant = new RaceParticipant();
        participant.id = UUID.randomUUID();
        participant.raceId = raceId;
        participant.riotPuuid = account.puuid();
        participant.riotGameName = account.gameName();
        participant.riotTagLine = account.tagLine();
        return participant;
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

    public UUID getRaceId() {
        return raceId;
    }

    public String getRiotPuuid() {
        return riotPuuid;
    }

    public String getRiotGameName() {
        return riotGameName;
    }

    public String getRiotTagLine() {
        return riotTagLine;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
