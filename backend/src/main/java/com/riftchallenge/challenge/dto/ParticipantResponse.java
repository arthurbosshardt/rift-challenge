package com.riftchallenge.challenge.dto;

import com.riftchallenge.challenge.ChallengeParticipant;
import java.util.UUID;

public record ParticipantResponse(
        UUID id,
        String gameName,
        String tagLine,
        String riotId
) {

    public static ParticipantResponse from(ChallengeParticipant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getRiotGameName(),
                participant.getRiotTagLine(),
                participant.getRiotGameName() + "#" + participant.getRiotTagLine()
        );
    }
}
