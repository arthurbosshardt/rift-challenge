package com.riftchallenge.synchronization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rank_snapshot")
public class RankSnapshot {

    public enum SnapshotType {
        BASELINE,
        REFRESH
    }

    @Id
    private UUID id;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "snapshot_type", nullable = false, length = 16)
    private SnapshotType snapshotType;

    @Column(name = "queue_type", nullable = false, length = 32)
    private String queueType;

    @Column(nullable = false, length = 16)
    private String tier;

    @Column(name = "rank_division", length = 4)
    private String rankDivision;

    @Column(name = "league_points", nullable = false)
    private int leaguePoints;

    private Integer wins;

    private Integer losses;

    protected RankSnapshot() {
    }

    public static RankSnapshot create(
            UUID participantId,
            Instant capturedAt,
            SnapshotType snapshotType,
            String queueType,
            String tier,
            String rankDivision,
            int leaguePoints,
            Integer wins,
            Integer losses
    ) {
        RankSnapshot snapshot = new RankSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.participantId = participantId;
        snapshot.capturedAt = capturedAt;
        snapshot.snapshotType = snapshotType;
        snapshot.queueType = queueType;
        snapshot.tier = tier;
        snapshot.rankDivision = rankDivision;
        snapshot.leaguePoints = leaguePoints;
        snapshot.wins = wins;
        snapshot.losses = losses;
        return snapshot;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public SnapshotType getSnapshotType() {
        return snapshotType;
    }

    public Instant getCapturedAt() {
        return capturedAt;
    }

    public String getTier() {
        return tier;
    }

    public String getRankDivision() {
        return rankDivision;
    }

    public int getLeaguePoints() {
        return leaguePoints;
    }

    public Integer getWins() {
        return wins;
    }

    public Integer getLosses() {
        return losses;
    }
}
