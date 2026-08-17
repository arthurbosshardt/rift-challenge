package com.riftchallenge.challenge;

import com.riftchallenge.riot.dto.RiotAccountDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "challenge_participant")
public class ChallengeParticipant {

    @Id
    private UUID id;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "riot_puuid", nullable = false, length = 78)
    private String riotPuuid;

    @Column(name = "riot_game_name", nullable = false, length = 16)
    private String riotGameName;

    @Column(name = "riot_tag_line", nullable = false, length = 5)
    private String riotTagLine;

    @Column(name = "duo_id")
    private UUID duoId;

    @Column(name = "profile_icon_id")
    private Integer profileIconId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChallengeParticipant() {
    }

    public static ChallengeParticipant create(UUID challengeId, RiotAccountDto account) {
        return create(challengeId, account, null);
    }

    public static ChallengeParticipant create(UUID challengeId, RiotAccountDto account, UUID duoId) {
        ChallengeParticipant participant = new ChallengeParticipant();
        participant.id = UUID.randomUUID();
        participant.challengeId = challengeId;
        participant.riotPuuid = account.puuid();
        participant.riotGameName = account.gameName();
        participant.riotTagLine = account.tagLine();
        participant.duoId = duoId;
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

    public UUID getChallengeId() {
        return challengeId;
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

    public UUID getDuoId() {
        return duoId;
    }

    public Integer getProfileIconId() {
        return profileIconId;
    }

    public void updateProfileIconId(Integer profileIconId) {
        this.profileIconId = profileIconId;
    }
}
