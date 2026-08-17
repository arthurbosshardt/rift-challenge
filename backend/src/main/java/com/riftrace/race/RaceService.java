package com.riftrace.race;

import com.riftrace.account.AppUserRepository;
import com.riftrace.race.dto.CreateRaceRequest;
import com.riftrace.race.dto.DuoProgressResponse;
import com.riftrace.race.dto.ParticipantProgressResponse;
import com.riftrace.race.dto.RaceDetailResponse;
import com.riftrace.race.dto.RaceSummaryResponse;
import com.riftrace.synchronization.RaceSyncService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RaceService {

    private final RaceRepository raceRepository;
    private final AppUserRepository appUserRepository;
    private final RaceParticipantRepository participantRepository;
    private final RaceProgressService progressService;
    private final RaceDuoProgressService duoProgressService;
    private final ParticipantProfileService participantProfileService;
    private final RaceRefreshRepository raceRefreshRepository;
    private final RaceSyncService raceSyncService;
    private final Clock clock;

    public RaceService(
            RaceRepository raceRepository,
            AppUserRepository appUserRepository,
            RaceParticipantRepository participantRepository,
            RaceProgressService progressService,
            RaceDuoProgressService duoProgressService,
            ParticipantProfileService participantProfileService,
            RaceRefreshRepository raceRefreshRepository,
            RaceSyncService raceSyncService,
            Clock clock
    ) {
        this.raceRepository = raceRepository;
        this.appUserRepository = appUserRepository;
        this.participantRepository = participantRepository;
        this.progressService = progressService;
        this.duoProgressService = duoProgressService;
        this.participantProfileService = participantProfileService;
        this.raceRefreshRepository = raceRefreshRepository;
        this.raceSyncService = raceSyncService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RaceSummaryResponse> listPublicRaces() {
        Instant now = clock.instant();
        return raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(now).stream()
                .map(race -> RaceSummaryResponse.from(race, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RaceSummaryResponse> listMyRaces(UUID ownerId) {
        Instant now = clock.instant();
        return raceRepository.findByOwnerIdOrderByStartAtDesc(ownerId).stream()
                .map(race -> RaceSummaryResponse.from(race, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public RaceDetailResponse getByShareSlug(UUID shareSlug, UUID callerId) {
        Race race = raceRepository.findByShareSlug(shareSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Race not found"));
        return toDetailResponse(race, callerId);
    }

    @Transactional(readOnly = true)
    public RaceDetailResponse getById(UUID raceId, UUID callerId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Race not found"));
        return toDetailResponse(race, callerId);
    }

    @Transactional
    public RaceDetailResponse createRace(UUID ownerId, CreateRaceRequest request) {
        if (!appUserRepository.existsById(ownerId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown owner");
        }

        Race race = Race.create(
                ownerId,
                request.name().trim(),
                request.type(),
                request.startAt(),
                request.isPublic()
        );
        return toDetailResponse(raceRepository.save(race), ownerId);
    }

    @Transactional
    public RaceDetailResponse refreshRace(UUID raceId, UUID callerId) {
        raceSyncService.refreshRace(raceId);
        return getById(raceId, callerId);
    }

    private RaceDetailResponse toDetailResponse(Race race, UUID callerId) {
        Instant now = clock.instant();
        List<RaceParticipant> raceParticipants = participantRepository.findByRaceIdOrderByCreatedAtAsc(race.getId());
        raceParticipants.stream()
                .filter(participant -> participant.getProfileIconId() == null)
                .forEach(participant -> participantProfileService.ensureProfileIcon(participant.getId()));
        raceParticipants = participantRepository.findByRaceIdOrderByCreatedAtAsc(race.getId());

        List<ParticipantProgressResponse> participants;
        List<DuoProgressResponse> duos;

        if (race.getType() == RaceType.DUOQ) {
            participants = List.of();
            duos = duoProgressService.buildProgress(race.getId());
        } else {
            participants = progressService.buildProgress(raceParticipants);
            duos = List.of();
        }

        RefreshTiming refreshTiming = resolveRefreshTiming(race.getId(), now);

        return RaceDetailResponse.from(
                race,
                now,
                participants,
                duos,
                callerId,
                refreshTiming.lastRefreshedAt(),
                refreshTiming.refreshAvailable(),
                refreshTiming.nextRefreshAvailableAt()
        );
    }

    private RefreshTiming resolveRefreshTiming(UUID raceId, Instant now) {
        return raceRefreshRepository.findByRaceId(raceId)
                .map(refresh -> {
                    Instant lastRefreshedAt = refresh.getRefreshedAt();
                    Instant nextAllowed = lastRefreshedAt.plus(RaceSyncService.REFRESH_COOLDOWN);
                    boolean available = !now.isBefore(nextAllowed);
                    return new RefreshTiming(
                            lastRefreshedAt,
                            available,
                            available ? null : nextAllowed
                    );
                })
                .orElse(new RefreshTiming(null, true, null));
    }

    private record RefreshTiming(
            Instant lastRefreshedAt,
            boolean refreshAvailable,
            Instant nextRefreshAvailableAt
    ) {
    }
}
