package com.riftrace.race.dto;

import com.riftrace.race.Race;
import com.riftrace.race.RaceType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RaceDetailResponse(
        UUID id,
        UUID shareSlug,
        String name,
        RaceType type,
        Instant startAt,
        Instant endAt,
        boolean isPublic,
        String status,
        String sharePath,
        boolean isOwner,
        Instant lastRefreshedAt,
        Instant nextRefreshAvailableAt,
        boolean refreshAvailable,
        List<ParticipantProgressResponse> participants,
        List<DuoProgressResponse> duos
) {

    public static RaceDetailResponse from(
            Race race,
            Instant now,
            List<ParticipantProgressResponse> participants,
            List<DuoProgressResponse> duos,
            UUID callerId,
            Instant lastRefreshedAt,
            boolean refreshAvailable,
            Instant nextRefreshAvailableAt
    ) {
        boolean isOwner = callerId != null && callerId.equals(race.getOwnerId());
        String status = RaceSummaryResponse.resolveStatus(race.getStartAt(), race.getEndAt(), now);
        boolean raceActive = "ACTIVE".equals(status);

        return new RaceDetailResponse(
                race.getId(),
                race.getShareSlug(),
                race.getName(),
                race.getType(),
                race.getStartAt(),
                race.getEndAt(),
                race.isPublic(),
                status,
                "/races/" + race.getShareSlug(),
                isOwner,
                lastRefreshedAt,
                nextRefreshAvailableAt,
                raceActive && refreshAvailable,
                participants,
                duos
        );
    }
}
