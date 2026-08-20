package com.riftchallenge.challenge;

import com.riftchallenge.account.AppUserRepository;
import com.riftchallenge.account.UserRiotAccountService;
import com.riftchallenge.challenge.ChallengeSummaryCacheService.ChallengeListSnapshot;
import com.riftchallenge.challenge.ChallengeSummaryCacheService.RefreshEligibility;
import com.riftchallenge.challenge.dto.CreateChallengeRequest;
import com.riftchallenge.challenge.dto.ChallengeDetailResponse;
import com.riftchallenge.challenge.dto.ChallengeListResponse;
import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import com.riftchallenge.challenge.dto.DuoProgressResponse;
import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.challenge.dto.UpdateChallengeEndRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeNameRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeScheduleRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeStartRequest;
import com.riftchallenge.synchronization.ChallengeSyncService;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final AppUserRepository appUserRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeProgressService progressService;
    private final ChallengeDuoProgressService duoProgressService;
    private final ParticipantProfileService participantProfileService;
    private final ChallengeRefreshRepository challengeRefreshRepository;
    private final ChallengeSyncService challengeSyncService;
    private final ChallengeSummaryCacheService summaryCacheService;
    private final UserRiotAccountService userRiotAccountService;
    private final Clock clock;

    public ChallengeService(
            ChallengeRepository challengeRepository,
            AppUserRepository appUserRepository,
            ChallengeParticipantRepository participantRepository,
            ChallengeProgressService progressService,
            ChallengeDuoProgressService duoProgressService,
            ParticipantProfileService participantProfileService,
            ChallengeRefreshRepository challengeRefreshRepository,
            ChallengeSyncService challengeSyncService,
            ChallengeSummaryCacheService summaryCacheService,
            UserRiotAccountService userRiotAccountService,
            Clock clock
    ) {
        this.challengeRepository = challengeRepository;
        this.appUserRepository = appUserRepository;
        this.participantRepository = participantRepository;
        this.progressService = progressService;
        this.duoProgressService = duoProgressService;
        this.participantProfileService = participantProfileService;
        this.challengeRefreshRepository = challengeRefreshRepository;
        this.challengeSyncService = challengeSyncService;
        this.summaryCacheService = summaryCacheService;
        this.userRiotAccountService = userRiotAccountService;
        this.clock = clock;
    }

    @Transactional
    public ChallengeListResponse refreshPublicChallenges() {
        summaryCacheService.enforceCooldown();
        ChallengeListSnapshot snapshot = summaryCacheService.refreshAll(clock.instant());
        RefreshEligibility eligibility = summaryCacheService.eligibility();
        return toListResponse(snapshot.challenges(), snapshot.generatedAt(), eligibility);
    }

    @Transactional
    public ChallengeListResponse listPublicChallenges(String challengeName, String summoner, ChallengeType type) {
        ChallengeListSnapshot snapshot = summaryCacheService.readOrBootstrap();
        RefreshEligibility eligibility = summaryCacheService.eligibility();
        Instant now = clock.instant();

        List<ChallengeSummaryResponse> candidates = snapshot.challenges().stream()
                .filter(challenge -> !challenge.startAt().isAfter(now))
                .filter(challenge -> type == null || challenge.type() == type)
                .toList();

        String normalizedChallengeName = normalizeChallengeNameSearch(challengeName);
        String normalizedSummoner = normalizeSummonerSearch(summoner);

        if (normalizedChallengeName == null && normalizedSummoner == null) {
            return toListResponse(candidates, snapshot.generatedAt(), eligibility);
        }

        Set<UUID> candidateIds = candidates.stream()
                .map(ChallengeSummaryResponse::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> matchingChallengeIds = null;

        if (normalizedChallengeName != null) {
            Set<UUID> nameMatches = new LinkedHashSet<>();
            for (ChallengeSummaryResponse challenge : candidates) {
                if (matchesSearchTerm(challenge.name(), normalizedChallengeName)) {
                    nameMatches.add(challenge.id());
                }
            }
            matchingChallengeIds = nameMatches;
        }

        if (normalizedSummoner != null) {
            Set<UUID> summonerMatches = participantRepository
                    .findDistinctPublicChallengeIdsByParticipantSearch(now, normalizedSummoner)
                    .stream()
                    .filter(candidateIds::contains)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

            matchingChallengeIds = matchingChallengeIds == null
                    ? summonerMatches
                    : matchingChallengeIds.stream().filter(summonerMatches::contains)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        }

        final Set<UUID> finalMatchingChallengeIds = matchingChallengeIds == null ? Set.of() : matchingChallengeIds;

        List<ChallengeSummaryResponse> filtered = candidates.stream()
                .filter(challenge -> finalMatchingChallengeIds.contains(challenge.id()))
                .toList();
        return toListResponse(filtered, snapshot.generatedAt(), eligibility);
    }

    @Transactional
    public ChallengeListResponse listParticipatingChallenges(UUID userId) {
        List<String> linkedPuids = userRiotAccountService.listLinkedPuids(userId);
        ChallengeListSnapshot snapshot = summaryCacheService.readOrBootstrap();
        RefreshEligibility eligibility = summaryCacheService.eligibility();

        if (linkedPuids.isEmpty()) {
            return toListResponse(List.of(), snapshot.generatedAt(), eligibility);
        }

        List<UUID> challengeIds = participantRepository.findDistinctChallengeIdsByRiotPuuidIn(linkedPuids);
        if (challengeIds.isEmpty()) {
            return toListResponse(List.of(), snapshot.generatedAt(), eligibility);
        }

        List<ChallengeSummaryResponse> summaries = summaryCacheService.readByChallengeIds(challengeIds);
        return toListResponse(summaries, snapshot.generatedAt(), eligibility);
    }

    private static ChallengeListResponse toListResponse(
            List<ChallengeSummaryResponse> challenges,
            Instant generatedAt,
            RefreshEligibility eligibility
    ) {
        return new ChallengeListResponse(
                challenges,
                generatedAt,
                eligibility.refreshAvailable(),
                eligibility.nextRefreshAvailableAt()
        );
    }

    @Transactional(readOnly = true)
    public ChallengeDetailResponse getByShareSlug(String shareSlug, UUID callerId) {
        Challenge challenge = challengeRepository.findByShareSlug(shareSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        return toDetailResponse(challenge, callerId);
    }

    @Transactional(readOnly = true)
    public ChallengeDetailResponse getById(UUID challengeId, UUID callerId) {
        return getById(challengeId, callerId, true);
    }

    private ChallengeDetailResponse getById(UUID challengeId, UUID callerId, boolean includeMatchHistory) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        return toDetailResponse(challenge, callerId, includeMatchHistory);
    }

    @Transactional
    public ChallengeDetailResponse createChallenge(UUID ownerId, CreateChallengeRequest request) {
        if (!appUserRepository.existsById(ownerId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown owner");
        }

        requireValidEndMode(request.startAt(), request.endAt(), request.maxGames());

        String name = request.name().trim();
        requireUniqueChallengeName(name, null);

        Challenge challenge = Challenge.create(
                ownerId,
                name,
                request.type(),
                request.startAt(),
                request.endAt(),
                request.maxGames()
        );
        Challenge saved = challengeRepository.save(challenge);
        summaryCacheService.upsertOne(saved, clock.instant());
        return toDetailResponse(saved, ownerId, false);
    }

    @Transactional
    public ChallengeDetailResponse updateSchedule(UUID challengeId, UUID ownerId, UpdateChallengeScheduleRequest request) {
        Challenge challenge = requireOwnedChallenge(challengeId, ownerId);
        return saveSchedule(challenge, request.startAt(), request.endAt(), ownerId);
    }

    @Transactional
    public ChallengeDetailResponse updateStartAt(UUID challengeId, UUID ownerId, UpdateChallengeStartRequest request) {
        Challenge challenge = requireOwnedChallenge(challengeId, ownerId);
        if (challenge.getEndAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date is required");
        }
        return saveSchedule(challenge, request.startAt(), challenge.getEndAt(), ownerId);
    }

    @Transactional
    public ChallengeDetailResponse updateEndAt(UUID challengeId, UUID ownerId, UpdateChallengeEndRequest request) {
        Challenge challenge = requireOwnedChallenge(challengeId, ownerId);
        return saveSchedule(challenge, challenge.getStartAt(), request.endAt(), ownerId);
    }

    @Transactional
    public ChallengeDetailResponse updateName(UUID challengeId, UUID ownerId, UpdateChallengeNameRequest request) {
        Challenge challenge = requireOwnedChallenge(challengeId, ownerId);
        String name = request.name().trim();
        requireUniqueChallengeName(name, challenge.getId());
        challenge.updateName(name);
        challengeRepository.save(challenge);
        return toMetadataDetailResponse(challenge, ownerId);
    }

    @Transactional
    public ChallengeDetailResponse updateChallenge(UUID challengeId, UUID ownerId, UpdateChallengeRequest request) {
        Challenge challenge = requireOwnedChallenge(challengeId, ownerId);

        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
            }
            requireUniqueChallengeName(name, challenge.getId());
            challenge.updateName(name);
        }

        if (request.endAt() != null && request.maxGames() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only one of end date or max games can be set");
        }

        boolean scheduleChanged = request.startAt() != null || request.endAt() != null || request.maxGames() != null;
        if (scheduleChanged) {
            Instant startAt = request.startAt() != null ? request.startAt() : challenge.getStartAt();
            Instant endAt;
            Integer maxGames;
            if (request.endAt() != null) {
                endAt = request.endAt();
                maxGames = null;
            } else if (request.maxGames() != null) {
                endAt = null;
                maxGames = request.maxGames();
            } else {
                endAt = challenge.getEndAt();
                maxGames = challenge.getMaxGames();
            }
            requireValidEndMode(startAt, endAt, maxGames);
            challenge.updateStartAt(startAt);
            challenge.updateEndAt(endAt);
            challenge.updateMaxGames(maxGames);
        }

        challengeRepository.save(challenge);
        return toMetadataDetailResponse(challenge, ownerId);
    }

    @Transactional
    public void deleteChallenge(UUID challengeId, UUID ownerId) {
        Challenge challenge = requireOwnedChallenge(challengeId, ownerId);
        challengeRepository.delete(challenge);
    }

    private ChallengeDetailResponse saveSchedule(Challenge challenge, Instant startAt, Instant endAt, UUID ownerId) {
        requireEndAfterStart(startAt, endAt);
        challenge.updateStartAt(startAt);
        challenge.updateEndAt(endAt);
        challengeRepository.save(challenge);
        return toMetadataDetailResponse(challenge, ownerId);
    }

    private Challenge requireOwnedChallenge(UUID challengeId, UUID ownerId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        if (!challenge.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the challenge owner can modify this challenge");
        }
        return challenge;
    }

    public ChallengeDetailResponse refreshChallenge(UUID challengeId, UUID callerId) {
        if (!challengeRepository.existsById(challengeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found");
        }
        challengeSyncService.refreshChallenge(challengeId);
        return getById(challengeId, callerId);
    }

    private static void requireEndAfterStart(Instant startAt, Instant endAt) {
        if (endAt == null || !endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
    }

    private static void requireValidEndMode(Instant startAt, Instant endAt, Integer maxGames) {
        if (endAt != null && maxGames != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only one of end date or max games can be set");
        }
        if (endAt == null && maxGames == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date or max games is required");
        }
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after start date");
        }
        if (maxGames != null && maxGames <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Max games must be greater than zero");
        }
    }

    private void requireUniqueChallengeName(String name, UUID excludeChallengeId) {
        boolean nameTaken = excludeChallengeId == null
                ? challengeRepository.existsByNameIgnoreCase(name)
                : challengeRepository.existsByNameIgnoreCaseAndIdNot(name, excludeChallengeId);
        if (nameTaken) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Challenge name already taken");
        }
    }

    private ChallengeDetailResponse toDetailResponse(Challenge challenge, UUID callerId) {
        return toDetailResponse(challenge, callerId, true);
    }

    private ChallengeDetailResponse toMetadataDetailResponse(Challenge challenge, UUID callerId) {
        Instant now = clock.instant();
        RefreshTiming refreshTiming = resolveRefreshTiming(challenge, now);
        return ChallengeDetailResponse.from(
                challenge,
                now,
                List.of(),
                List.of(),
                callerId,
                refreshTiming.lastRefreshedAt(),
                refreshTiming.refreshAvailable(),
                refreshTiming.nextRefreshAvailableAt(),
                false
        );
    }

    private ChallengeDetailResponse toDetailResponse(
            Challenge challenge,
            UUID callerId,
            boolean includeMatchHistory
    ) {
        Instant now = clock.instant();
        List<ChallengeParticipant> challengeParticipants = participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId());
        if (includeMatchHistory) {
            challengeParticipants.stream()
                    .filter(participant -> participant.getProfileIconId() == null)
                    .forEach(participant -> participantProfileService.ensureProfileIcon(participant.getId()));
            challengeParticipants = participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId());
        }

        List<ParticipantProgressResponse> participants;
        List<DuoProgressResponse> duos;

        if (challenge.getType() == ChallengeType.DUOQ) {
            participants = List.of();
            duos = includeMatchHistory
                    ? duoProgressService.buildProgress(challenge)
                    : duoProgressService.buildPreview(challenge);
        } else {
            participants = includeMatchHistory
                    ? progressService.buildProgress(challenge, challengeParticipants)
                    : progressService.buildPreviewProgress(challenge, challengeParticipants);
            duos = List.of();
        }

        RefreshTiming refreshTiming = resolveRefreshTiming(challenge, now);

        boolean started = !now.isBefore(challenge.getStartAt());
        boolean allReachedMaxGames = challenge.getType() == ChallengeType.DUOQ
                ? allDuosReachedMaxGames(challenge, started, duos)
                : started && allParticipantsReachedMaxGames(challenge, participants);

        return ChallengeDetailResponse.from(
                challenge,
                now,
                participants,
                duos,
                callerId,
                refreshTiming.lastRefreshedAt(),
                refreshTiming.refreshAvailable(),
                refreshTiming.nextRefreshAvailableAt(),
                allReachedMaxGames
        );
    }

    private RefreshTiming resolveRefreshTiming(Challenge challenge, Instant now) {
        Instant lastDataUpdate = challenge.getDataSyncedAt();
        Optional<ChallengeRefresh> refresh = challengeRefreshRepository.findByChallengeId(challenge.getId());
        if (lastDataUpdate == null) {
            lastDataUpdate = refresh.map(ChallengeRefresh::getRefreshedAt).orElse(null);
        }

        if (lastDataUpdate == null) {
            return new RefreshTiming(null, true, null);
        }

        Instant nextAllowed = refresh
                .map(existing -> existing.getRefreshedAt().plus(ChallengeSyncService.REFRESH_COOLDOWN))
                .orElse(lastDataUpdate.plus(ChallengeSyncService.REFRESH_COOLDOWN));
        boolean available = !now.isBefore(nextAllowed);
        return new RefreshTiming(
                lastDataUpdate,
                available,
                available ? null : nextAllowed
        );
    }

    private record RefreshTiming(
            Instant lastRefreshedAt,
            boolean refreshAvailable,
            Instant nextRefreshAvailableAt
    ) {
    }

    private static boolean allParticipantsReachedMaxGames(Challenge challenge, List<ParticipantProgressResponse> progress) {
        return challenge.getMaxGames() != null
                && !progress.isEmpty()
                && progress.stream().allMatch(p -> p.wins() + p.losses() >= challenge.getMaxGames());
    }

    private static boolean allDuosReachedMaxGames(Challenge challenge, boolean started, List<DuoProgressResponse> duos) {
        return started
                && challenge.getMaxGames() != null
                && !duos.isEmpty()
                && duos.stream().allMatch(d -> d.wins() + d.losses() >= challenge.getMaxGames());
    }

    private static String normalizeChallengeNameSearch(String search) {
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
