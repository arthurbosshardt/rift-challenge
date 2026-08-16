package com.riftrace.synchronization;

import com.riftrace.race.Race;
import com.riftrace.race.RaceParticipant;
import com.riftrace.race.RaceParticipantRepository;
import com.riftrace.race.RaceRefreshRecordService;
import com.riftrace.race.RaceRefreshRepository;
import com.riftrace.race.RaceRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RaceSyncService {

    public static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(2);

    private final RaceRepository raceRepository;
    private final RaceParticipantRepository participantRepository;
    private final RaceRefreshRepository raceRefreshRepository;
    private final RaceParticipantSyncService participantSyncService;
    private final RaceRefreshRecordService refreshRecordService;
    private final Clock clock;

    public RaceSyncService(
            RaceRepository raceRepository,
            RaceParticipantRepository participantRepository,
            RaceRefreshRepository raceRefreshRepository,
            RaceParticipantSyncService participantSyncService,
            RaceRefreshRecordService refreshRecordService,
            Clock clock
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.raceRefreshRepository = raceRefreshRepository;
        this.participantSyncService = participantSyncService;
        this.refreshRecordService = refreshRecordService;
        this.clock = clock;
    }

    @Transactional
    public Instant refreshRace(UUID raceId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Race not found"));

        Instant now = clock.instant();
        if (now.isBefore(race.getStartAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Race has not started yet");
        }

        enforceCooldown(raceId, now);

        List<RaceParticipant> participants = participantRepository.findByRaceIdOrderByCreatedAtAsc(race.getId());
        boolean rateLimited = false;

        for (RaceParticipant participant : participants) {
            try {
                participantSyncService.syncParticipant(race, participant, now);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    rateLimited = true;
                    break;
                }
                throw exception;
            }
        }

        refreshRecordService.recordRefresh(raceId, now);

        if (rateLimited) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Riot API rate limit reached; partial sync saved"
            );
        }

        return now;
    }

    private void enforceCooldown(UUID raceId, Instant now) {
        raceRefreshRepository.findByRaceId(raceId).ifPresent(refresh -> {
            Instant nextAllowed = refresh.getRefreshedAt().plus(REFRESH_COOLDOWN);
            if (now.isBefore(nextAllowed)) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Refresh available at " + nextAllowed
                );
            }
        });
    }
}
