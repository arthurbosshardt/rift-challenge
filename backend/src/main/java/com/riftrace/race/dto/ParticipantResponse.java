package com.riftrace.race.dto;

import com.riftrace.race.RaceParticipant;
import java.util.UUID;

public record ParticipantResponse(
        UUID id,
        String gameName,
        String tagLine,
        String riotId
) {

    public static ParticipantResponse from(RaceParticipant participant) {
        return new ParticipantResponse(
                participant.getId(),
                participant.getRiotGameName(),
                participant.getRiotTagLine(),
                participant.getRiotGameName() + "#" + participant.getRiotTagLine()
        );
    }
}
