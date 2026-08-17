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
        Instant endAt,
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
                race.getEndAt(),
                race.isPublic(),
                resolveStatus(race.getStartAt(), race.getEndAt(), now)
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
