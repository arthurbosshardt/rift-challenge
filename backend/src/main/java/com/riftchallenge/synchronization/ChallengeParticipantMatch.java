package com.riftchallenge.synchronization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "challenge_participant_match")
public class ChallengeParticipantMatch {

    @Id
    private UUID id;

    @Column(name = "challenge_id", nullable = false)
    private UUID challengeId;

    @Column(name = "participant_id", nullable = false)
    private UUID participantId;

    @Column(name = "riot_match_id", nullable = false, length = 32)
    private String riotMatchId;

    @Column(nullable = false)
    private boolean win;

    @Column(name = "champion_id")
    private Integer championId;

    protected ChallengeParticipantMatch() {
    }

    public static ChallengeParticipantMatch create(
            UUID challengeId,
            UUID participantId,
            String riotMatchId,
            boolean win,
            Integer championId
    ) {
        ChallengeParticipantMatch link = new ChallengeParticipantMatch();
        link.id = UUID.randomUUID();
        link.challengeId = challengeId;
        link.participantId = participantId;
        link.riotMatchId = riotMatchId;
        link.win = win;
        link.championId = championId;
        return link;
    }

    public UUID getParticipantId() {
        return participantId;
    }

    public boolean isWin() {
        return win;
    }

    public Integer getChampionId() {
        return championId;
    }

    public String getRiotMatchId() {
        return riotMatchId;
    }

    public void updateChampionId(Integer championId) {
        this.championId = championId;
    }
}
