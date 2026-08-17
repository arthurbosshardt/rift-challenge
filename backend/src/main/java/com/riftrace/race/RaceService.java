package com.riftrace.race;

import com.riftrace.account.AppUserRepository;
import com.riftrace.account.UserRiotAccountService;
import com.riftrace.race.dto.CreateRaceRequest;
import com.riftrace.race.dto.DuoPreviewResponse;
import com.riftrace.race.dto.DuoProgressResponse;
import com.riftrace.race.dto.ParticipantPreviewResponse;
import com.riftrace.race.dto.ParticipantProgressResponse;
import com.riftrace.race.dto.RaceDetailResponse;
import com.riftrace.race.dto.RaceSummaryResponse;
import com.riftrace.race.dto.UpdateRaceEndRequest;
import com.riftrace.race.dto.UpdateRaceNameRequest;
import com.riftrace.race.dto.UpdateRaceScheduleRequest;
import com.riftrace.race.dto.UpdateRaceStartRequest;
import com.riftrace.race.dto.UpdateRaceVisibilityRequest;
import com.riftrace.synchronization.RaceSyncService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final UserRiotAccountService userRiotAccountService;
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
            UserRiotAccountService userRiotAccountService,
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
        this.userRiotAccountService = userRiotAccountService;
        this.clock = clock;
    }

    private static final int PREVIEW_LIMIT = 10;

    @Transactional(readOnly = true)
    public List<RaceSummaryResponse> listPublicRaces() {
        return listPublicRaces(null, null, null);
    }

    @Transactional(readOnly = true)
    public List<RaceSummaryResponse> listPublicRaces(String raceName, String summoner, RaceType type) {
        Instant now = clock.instant();
        List<Race> publicRaces = raceRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(now);

        List<Race> candidates = type == null
                ? publicRaces
                : publicRaces.stream().filter(race -> race.getType() == type).toList();

        String normalizedRaceName = normalizeRaceNameSearch(raceName);
        String normalizedSummoner = normalizeSummonerSearch(summoner);

        if (normalizedRaceName == null && normalizedSummoner == null) {
            return candidates.stream()
                    .map(race -> toSummaryResponse(race, now))
                    .toList();
        }

        Set<UUID> candidateIds = candidates.stream()
                .map(Race::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> matchingRaceIds = null;

        if (normalizedRaceName != null) {
            Set<UUID> nameMatches = new LinkedHashSet<>();
            for (Race race : candidates) {
                if (matchesSearchTerm(race.getName(), normalizedRaceName)) {
                    nameMatches.add(race.getId());
                }
            }
            matchingRaceIds = nameMatches;
        }

        if (normalizedSummoner != null) {
            Set<UUID> summonerMatches = participantRepository
                    .findDistinctPublicRaceIdsByParticipantSearch(now, normalizedSummoner)
                    .stream()
                    .filter(candidateIds::contains)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

            matchingRaceIds = matchingRaceIds == null
                    ? summonerMatches
                    : matchingRaceIds.stream().filter(summonerMatches::contains)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        final Set<UUID> finalMatchingRaceIds = matchingRaceIds == null ? Set.of() : matchingRaceIds;

        return candidates.stream()
                .filter(race -> finalMatchingRaceIds.contains(race.getId()))
                .map(race -> toSummaryResponse(race, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RaceSummaryResponse> listOwnedRaces(UUID ownerId) {
        Instant now = clock.instant();
        return raceRepository.findByOwnerIdOrderByStartAtDesc(ownerId).stream()
                .map(race -> toSummaryResponse(race, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RaceSummaryResponse> listParticipatingRaces(UUID userId) {
        List<String> linkedPuids = userRiotAccountService.listLinkedPuids(userId);
        if (linkedPuids.isEmpty()) {
            return List.of();
        }

        List<UUID> raceIds = participantRepository.findDistinctRaceIdsByRiotPuuidIn(linkedPuids);
        if (raceIds.isEmpty()) {
            return List.of();
        }

        Instant now = clock.instant();
        return raceRepository.findByIdInOrderByStartAtDesc(raceIds).stream()
                .map(race -> toSummaryResponse(race, now))
                .toList();
    }

    private RaceSummaryResponse toSummaryResponse(Race race, Instant now) {
        String status = RaceSummaryResponse.resolveStatus(race.getStartAt(), race.getEndAt(), now);
        boolean started = !"NOT_STARTED".equals(status);

        if (race.getType() == RaceType.DUOQ) {
            List<DuoProgressResponse> duos = duoProgressService.buildProgress(race.getId());
            List<String> participantGameNames = duos.stream()
                    .flatMap(duo -> java.util.stream.Stream.of(
                            duo.player1().gameName(),
                            duo.player2().gameName()))
                    .toList();
            List<DuoPreviewResponse> previewDuos = duos.stream()
                    .limit(PREVIEW_LIMIT)
                    .map(DuoPreviewResponse::from)
                    .toList();
            return RaceSummaryResponse.from(
                    race, now, duos.size(), participantGameNames, List.of(), previewDuos);
        }

        List<RaceParticipant> participants = participantRepository.findByRaceIdOrderByCreatedAtAsc(race.getId());
        List<String> participantGameNames = participants.stream()
                .map(RaceParticipant::getRiotGameName)
                .toList();
        List<ParticipantPreviewResponse> previewParticipants;

        if (started) {
            previewParticipants = progressService.buildProgress(participants).stream()
                    .limit(PREVIEW_LIMIT)
                    .map(ParticipantPreviewResponse::from)
                    .toList();
        } else {
            previewParticipants = new java.util.ArrayList<>();
            for (int index = 0; index < Math.min(PREVIEW_LIMIT, participants.size()); index++) {
                previewParticipants.add(
                        ParticipantPreviewResponse.fromParticipant(participants.get(index), index + 1)
                );
            }
        }

        return RaceSummaryResponse.from(
                race, now, participants.size(), participantGameNames, previewParticipants, List.of());
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

        requireEndAfterStart(request.startAt(), request.endAt());

        Race race = Race.create(
                ownerId,
                request.name().trim(),
                request.type(),
                request.startAt(),
                request.endAt(),
                request.isPublic()
        );
        return toDetailResponse(raceRepository.save(race), ownerId);
    }

    @Transactional
    public RaceDetailResponse updateSchedule(UUID raceId, UUID ownerId, UpdateRaceScheduleRequest request) {
        Race race = requireOwnedRace(raceId, ownerId);
        return saveSchedule(race, request.startAt(), request.endAt(), ownerId);
    }

    @Transactional
    public RaceDetailResponse updateStartAt(UUID raceId, UUID ownerId, UpdateRaceStartRequest request) {
        Race race = requireOwnedRace(raceId, ownerId);
        if (race.getEndAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date is required");
        }
        return saveSchedule(race, request.startAt(), race.getEndAt(), ownerId);
    }

    @Transactional
    public RaceDetailResponse updateEndAt(UUID raceId, UUID ownerId, UpdateRaceEndRequest request) {
        Race race = requireOwnedRace(raceId, ownerId);
        return saveSchedule(race, race.getStartAt(), request.endAt(), ownerId);
    }

    @Transactional
    public RaceDetailResponse updateVisibility(UUID raceId, UUID ownerId, UpdateRaceVisibilityRequest request) {
        Race race = requireOwnedRace(raceId, ownerId);
        race.updateVisibility(request.isPublic());
        raceRepository.save(race);
        return getById(race.getId(), ownerId);
    }

    @Transactional
    public RaceDetailResponse updateName(UUID raceId, UUID ownerId, UpdateRaceNameRequest request) {
        Race race = requireOwnedRace(raceId, ownerId);
        race.updateName(request.name().trim());
        raceRepository.save(race);
        return getById(race.getId(), ownerId);
    }

    private RaceDetailResponse saveSchedule(Race race, Instant startAt, Instant endAt, UUID ownerId) {
        requireEndAfterStart(startAt, endAt);
        race.updateStartAt(startAt);
        race.updateEndAt(endAt);
        raceRepository.save(race);
        maybeAutoRefreshAfterScheduleChange(race.getId(), startAt);
        return getById(race.getId(), ownerId);
    }

    private Race requireOwnedRace(UUID raceId, UUID ownerId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Race not found"));
        if (!race.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the race owner can update the race schedule");
        }
        return race;
    }

    private void maybeAutoRefreshAfterScheduleChange(UUID raceId, Instant startAt) {
        Instant now = clock.instant();
        RefreshTiming refreshTiming = resolveRefreshTiming(raceId, now);
        if (refreshTiming.refreshAvailable() && !now.isBefore(startAt)) {
            try {
                raceSyncService.refreshRace(raceId);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                    throw exception;
                }
            }
        }
    }

    @Transactional
    public RaceDetailResponse refreshRace(UUID raceId, UUID callerId) {
        raceSyncService.refreshRace(raceId);
        return getById(raceId, callerId);
    }

    private static void requireEndAfterStart(Instant startAt, Instant endAt) {
        if (endAt == null || !endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
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

    private static String normalizeRaceNameSearch(String search) {
        return normalizeSearchParam(search);
    }

    private static String normalizeSummonerSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String trimmed = search.trim();
        int hashIndex = trimmed.indexOf('#');
        if (hashIndex > 0) {
            trimmed = trimmed.substring(0, hashIndex);
        }

        return normalizeSearchParam(trimmed);
    }

    private static String normalizeSearchParam(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        String normalized = search.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        return normalized.length() >= 3 ? normalized : null;
    }

    private static boolean matchesSearchTerm(String value, String normalizedSearch) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "").contains(normalizedSearch);
    }
}
