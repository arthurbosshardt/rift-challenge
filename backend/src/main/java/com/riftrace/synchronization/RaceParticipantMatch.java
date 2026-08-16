package com.riftrace.synchronization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "race_participant_match")
public class RaceParticipantMatch {

    @Id
    private UUID id;

    @Column(name = "race_id", nullable = false)
    private UUID raceId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "riot_match_id", nullable = false, length = 32)
    private String riotMatchId;

    @Column(nullable = false)
    private boolean win;

    protected RaceParticipantMatch() {
    }

    public static RaceParticipantMatch create(UUID raceId, UUID participantId, String riotMatchId, boolean win) {
        RaceParticipantMatch link = new RaceParticipantMatch();
        link.id = UUID.randomUUID();
        link.raceId = raceId;
        link.participantId = participantId;
        link.riotMatchId = riotMatchId;
        link.win = win;
        return link;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public boolean isWin() {
        return win;
    }
}
