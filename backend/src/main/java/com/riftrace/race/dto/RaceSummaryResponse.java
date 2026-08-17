package com.riftrace.race.dto;

import com.riftrace.race.Race;
import com.riftrace.race.RaceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RaceSummaryResponse(
        UUID id,
        UUID shareSlug,
        String name,
        RaceType type,
        Instant startAt,
        Instant endAt,
        boolean isPublic,
        String status,
        int entryCount,
        List<String> participantGameNames,
        List<ParticipantPreviewResponse> previewParticipants,
        List<DuoPreviewResponse> previewDuos
) {

    public static RaceSummaryResponse from(
            Race race,
            Instant now,
            int entryCount,
            List<String> participantGameNames,
            List<ParticipantPreviewResponse> previewParticipants,
            List<DuoPreviewResponse> previewDuos
    ) {
        return new RaceSummaryResponse(
                race.getId(),
                race.getShareSlug(),
                race.getName(),
                race.getType(),
                race.getStartAt(),
                race.getEndAt(),
                race.isPublic(),
                resolveStatus(race.getStartAt(), race.getEndAt(), now),
                entryCount,
                participantGameNames,
                previewParticipants,
                previewDuos
        );
    }

    public static String resolveStatus(Instant startAt, Instant now) {
        return resolveStatus(startAt, null, now);
    }

    public static String resolveStatus(Instant startAt, Instant endAt, Instant now) {
        if (now.isBefore(startAt)) {
            return "NOT_STARTED";
        }
        if (endAt != null && !now.isBefore(endAt)) {
            return "FINISHED";
        }
        return "ACTIVE";
    }
}
