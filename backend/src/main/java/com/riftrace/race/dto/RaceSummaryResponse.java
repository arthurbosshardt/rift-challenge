package com.riftrace.race.dto;

import com.riftrace.race.Race;
import com.riftrace.race.RaceType;
import java.time.Instant;
import java.util.UUID;

public record RaceSummaryResponse(
        UUID id,
        UUID shareSlug,
        String name,
        RaceType type,
        Instant startAt,
        boolean isPublic,
        String status
) {

    public static RaceSummaryResponse from(Race race, Instant now) {
        return new RaceSummaryResponse(
                race.getId(),
                race.getShareSlug(),
                race.getName(),
                race.getType(),
                race.getStartAt(),
                race.isPublic(),
                resolveStatus(race.getStartAt(), now)
        );
    }

    public static String resolveStatus(Instant startAt, Instant now) {
        if (now.isBefore(startAt)) {
            return "NOT_STARTED";
        }
        return "ACTIVE";
    }
}
