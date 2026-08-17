package com.riftchallenge.challenge;

import com.riftchallenge.account.AppUserRepository;
import com.riftchallenge.account.UserRiotAccountService;
import com.riftchallenge.challenge.dto.CreateChallengeRequest;
import com.riftchallenge.challenge.dto.DuoPreviewResponse;
import com.riftchallenge.challenge.dto.DuoProgressResponse;
import com.riftchallenge.challenge.dto.ParticipantPreviewResponse;
import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.challenge.dto.ChallengeDetailResponse;
import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import com.riftchallenge.challenge.dto.UpdateChallengeEndRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeNameRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeScheduleRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeStartRequest;
import com.riftchallenge.challenge.dto.UpdateChallengeVisibilityRequest;
import com.riftchallenge.synchronization.ChallengeSyncService;
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
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final AppUserRepository appUserRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeProgressService progressService;
    private final ChallengeDuoProgressService duoProgressService;
    private final ParticipantProfileService participantProfileService;
    private final ChallengeRefreshRepository challengeRefreshRepository;
    private final ChallengeSyncService challengeSyncService;
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
        this.userRiotAccountService = userRiotAccountService;
        this.clock = clock;
    }

    private static final int PREVIEW_LIMIT = 10;

    @Transactional(readOnly = true)
    public List<ChallengeSummaryResponse> listPublicChallenges() {
        return listPublicChallenges(null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ChallengeSummaryResponse> listPublicChallenges(String challengeName, String summoner, ChallengeType type) {
        Instant now = clock.instant();
        List<Challenge> publicChallenges = challengeRepository.findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(now);

        List<Challenge> candidates = type == null
                ? publicChallenges
                : publicChallenges.stream().filter(challenge -> challenge.getType() == type).toList();

        String normalizedChallengeName = normalizeChallengeNameSearch(challengeName);
        String normalizedSummoner = normalizeSummonerSearch(summoner);

        if (normalizedChallengeName == null && normalizedSummoner == null) {
            return candidates.stream()
                    .map(challenge -> toSummaryResponse(challenge, now))
                    .toList();
        }

        Set<UUID> candidateIds = candidates.stream()
                .map(Challenge::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        Set<UUID> matchingChallengeIds = null;

        if (normalizedChallengeName != null) {
            Set<UUID> nameMatches = new LinkedHashSet<>();
            for (Challenge challenge : candidates) {
                if (matchesSearchTerm(challenge.getName(), normalizedChallengeName)) {
                    nameMatches.add(challenge.getId());
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

        return candidates.stream()
                .filter(challenge -> finalMatchingChallengeIds.contains(challenge.getId()))
                .map(challenge -> toSummaryResponse(challenge, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChallengeSummaryResponse> listOwnedChallenges(UUID ownerId) {
        Instant now = clock.instant();
        return challengeRepository.findByOwnerIdOrderByStartAtDesc(ownerId).stream()
                .map(challenge -> toSummaryResponse(challenge, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChallengeSummaryResponse> listParticipatingChallenges(UUID userId) {
        List<String> linkedPuids = userRiotAccountService.listLinkedPuids(userId);
        if (linkedPuids.isEmpty()) {
            return List.of();
        }

        List<UUID> challengeIds = participantRepository.findDistinctChallengeIdsByRiotPuuidIn(linkedPuids);
        if (challengeIds.isEmpty()) {
            return List.of();
        }

        Instant now = clock.instant();
        return challengeRepository.findByIdInOrderByStartAtDesc(challengeIds).stream()
                .map(challenge -> toSummaryResponse(challenge, now))
                .toList();
    }

    private ChallengeSummaryResponse toSummaryResponse(Challenge challenge, Instant now) {
        String status = ChallengeSummaryResponse.resolveStatus(challenge.getStartAt(), challenge.getEndAt(), now);
        boolean started = !"NOT_STARTED".equals(status);

        if (challenge.getType() == ChallengeType.DUOQ) {
            List<DuoProgressResponse> duos = duoProgressService.buildProgress(challenge.getId());
            List<String> participantGameNames = duos.stream()
                    .flatMap(duo -> java.util.stream.Stream.of(
                            duo.player1().gameName(),
                            duo.player2().gameName()))
                    .toList();
            List<DuoPreviewResponse> previewDuos = duos.stream()
                    .limit(PREVIEW_LIMIT)
                    .map(DuoPreviewResponse::from)
                    .toList();
            return ChallengeSummaryResponse.from(
                    challenge, now, duos.size(), participantGameNames, List.of(), previewDuos);
        }

        List<ChallengeParticipant> participants = participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId());
        List<String> participantGameNames = participants.stream()
                .map(ChallengeParticipant::getRiotGameName)
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

        return ChallengeSummaryResponse.from(
                challenge, now, participants.size(), participantGameNames, previewParticipants, List.of());
    }

    @Transactional(readOnly = true)
    public ChallengeDetailResponse getByShareSlug(UUID shareSlug, UUID callerId) {
        Challenge challenge = challengeRepository.findByShareSlug(shareSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        return toDetailResponse(challenge, callerId);
    }

    @Transactional(readOnly = true)
    public ChallengeDetailResponse getById(UUID challengeId, UUID callerId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        return toDetailResponse(challenge, callerId);
    }

    @Transactional
    public ChallengeDetailResponse createChallenge(UUID ownerId, CreateChallengeRequest request) {
        if (!appUserRepository.existsById(ownerId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown owner");
        }

        requireEndAfterStart(request.startAt(), request.endAt());

        String name = request.name().trim();
        requireUniqueChallengeName(name, null);

        Challenge challenge = Challenge.create(
                ownerId,
                name,
                request.type(),
                request.startAt(),
                request.endAt(),
                request.isPublic()
        );
        return toDetailResponse(challengeRepository.save(challenge), ownerId);
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
    public ChallengeDetailResponse updateVisibility(UUID challengeId, UUID ownerId, UpdateChallengeVisibilityRequest request) {
        Challenge challenge = requireOwnedChallenge(challengeId, ownerId);
        challenge.updateVisibility(request.isPublic());
        challengeRepository.save(challenge);
        return getById(challenge.getId(), ownerId);
    }

    @Transactional
    public ChallengeDetailResponse updateName(UUID challengeId, UUID ownerId, UpdateChallengeNameRequest request) {
        Challenge challenge = requireOwnedChallenge(challengeId, ownerId);
        String name = request.name().trim();
        requireUniqueChallengeName(name, challenge.getId());
        challenge.updateName(name);
        challengeRepository.save(challenge);
        return getById(challenge.getId(), ownerId);
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
        maybeAutoRefreshAfterScheduleChange(challenge.getId(), startAt);
        return getById(challenge.getId(), ownerId);
    }

    private Challenge requireOwnedChallenge(UUID challengeId, UUID ownerId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));
        if (!challenge.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the challenge owner can modify this challenge");
        }
        return challenge;
    }

    private void maybeAutoRefreshAfterScheduleChange(UUID challengeId, Instant startAt) {
        Instant now = clock.instant();
        RefreshTiming refreshTiming = resolveRefreshTiming(challengeId, now);
        if (refreshTiming.refreshAvailable() && !now.isBefore(startAt)) {
            try {
                challengeSyncService.refreshChallenge(challengeId);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) {
                    throw exception;
                }
            }
        }
    }

    @Transactional
    public ChallengeDetailResponse refreshChallenge(UUID challengeId, UUID callerId) {
        challengeSyncService.refreshChallenge(challengeId);
        return getById(challengeId, callerId);
    }

    private static void requireEndAfterStart(Instant startAt, Instant endAt) {
        if (endAt == null || !endAt.isAfter(startAt)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "End date must be after start date");
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
        Instant now = clock.instant();
        List<ChallengeParticipant> challengeParticipants = participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId());
        challengeParticipants.stream()
                .filter(participant -> participant.getProfileIconId() == null)
                .forEach(participant -> participantProfileService.ensureProfileIcon(participant.getId()));
        challengeParticipants = participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId());

        List<ParticipantProgressResponse> participants;
        List<DuoProgressResponse> duos;

        if (challenge.getType() == ChallengeType.DUOQ) {
            participants = List.of();
            duos = duoProgressService.buildProgress(challenge.getId());
        } else {
            participants = progressService.buildProgress(challengeParticipants);
            duos = List.of();
        }

        RefreshTiming refreshTiming = resolveRefreshTiming(challenge.getId(), now);

        return ChallengeDetailResponse.from(
                challenge,
                now,
                participants,
                duos,
                callerId,
                refreshTiming.lastRefreshedAt(),
                refreshTiming.refreshAvailable(),
                refreshTiming.nextRefreshAvailableAt()
        );
    }

    private RefreshTiming resolveRefreshTiming(UUID challengeId, Instant now) {
        return challengeRefreshRepository.findByChallengeId(challengeId)
                .map(refresh -> {
                    Instant lastRefreshedAt = refresh.getRefreshedAt();
                    Instant nextAllowed = lastRefreshedAt.plus(ChallengeSyncService.REFRESH_COOLDOWN);
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
