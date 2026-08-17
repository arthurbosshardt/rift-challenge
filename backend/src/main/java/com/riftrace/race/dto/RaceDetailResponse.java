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
        boolean isPublic,
        String status,
        String sharePath,
        UUID ownerId,
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
        boolean raceStarted = !now.isBefore(race.getStartAt());

        return new RaceDetailResponse(
                race.getId(),
                race.getShareSlug(),
                race.getName(),
                race.getType(),
                race.getStartAt(),
                race.isPublic(),
                RaceSummaryResponse.resolveStatus(race.getStartAt(), now),
                "/races/" + race.getShareSlug(),
                race.getOwnerId(),
                isOwner,
                lastRefreshedAt,
                nextRefreshAvailableAt,
                raceStarted && refreshAvailable,
                participants,
                duos
        );
    }
}
