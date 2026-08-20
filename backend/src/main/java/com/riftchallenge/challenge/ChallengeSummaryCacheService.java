package com.riftchallenge.challenge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riftchallenge.challenge.dto.DuoPreviewResponse;
import com.riftchallenge.challenge.dto.DuoProgressResponse;
import com.riftchallenge.challenge.dto.ParticipantPreviewResponse;
import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.challenge.dto.ChallengeSummaryResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Owns the BDD snapshot of {@link ChallengeSummaryResponse} for every challenge. Recomputing
 * this list is expensive (rank snapshots, win/loss aggregates, potential rank replay per
 * participant), so it is only recomputed on an explicit refresh (or lazily once, if the cache
 * has never been populated) rather than on every list read.
 */
@Service
public class ChallengeSummaryCacheService {

    private static final int PREVIEW_LIMIT = 10;

    private final ChallengeRepository challengeRepository;
    private final ChallengeSummaryCacheRepository summaryCacheRepository;
    private final ChallengeListCacheStateRepository stateRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeProgressService progressService;
    private final ChallengeDuoProgressService duoProgressService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration refreshCooldown;

    public ChallengeSummaryCacheService(
            ChallengeRepository challengeRepository,
            ChallengeSummaryCacheRepository summaryCacheRepository,
            ChallengeListCacheStateRepository stateRepository,
            ChallengeParticipantRepository participantRepository,
            ChallengeProgressService progressService,
            ChallengeDuoProgressService duoProgressService,
            ObjectMapper objectMapper,
            Clock clock,
            @Value("${riftchallenge.challenge-list-cache.refresh-cooldown:PT60S}") Duration refreshCooldown
    ) {
        this.challengeRepository = challengeRepository;
        this.summaryCacheRepository = summaryCacheRepository;
        this.stateRepository = stateRepository;
        this.participantRepository = participantRepository;
        this.progressService = progressService;
        this.duoProgressService = duoProgressService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.refreshCooldown = refreshCooldown;
    }

    public record ChallengeListSnapshot(List<ChallengeSummaryResponse> challenges, Instant generatedAt) {
    }

    public record RefreshEligibility(boolean refreshAvailable, Instant nextRefreshAvailableAt) {
    }

    @Transactional(readOnly = true)
    public Optional<ChallengeListSnapshot> readAll() {
        return stateRepository.findById(ChallengeListCacheState.SINGLETON_ID)
                .map(state -> new ChallengeListSnapshot(readCachedSummaries(), state.getGeneratedAt()));
    }

    @Transactional(readOnly = true)
    public List<ChallengeSummaryResponse> readByChallengeIds(Collection<java.util.UUID> challengeIds) {
        return summaryCacheRepository.findByChallengeIdInOrderByStartAtDesc(challengeIds).stream()
                .map(this::deserialize)
                .toList();
    }

    private List<ChallengeSummaryResponse> readCachedSummaries() {
        return summaryCacheRepository.findAllByOrderByStartAtDesc().stream()
                .map(this::deserialize)
                .toList();
    }

    /**
     * Reads the cache, populating it synchronously on the very first call (no snapshot has
     * ever been persisted yet). Subsequent calls read the persisted snapshot directly.
     */
    @Transactional
    public ChallengeListSnapshot readOrBootstrap() {
        return readAll().orElseGet(() -> refreshAll(clock.instant()));
    }

    @Transactional(readOnly = true)
    public RefreshEligibility eligibility() {
        Instant now = clock.instant();
        return stateRepository.findById(ChallengeListCacheState.SINGLETON_ID)
                .map(state -> eligibilityFrom(state.getGeneratedAt(), now))
                .orElse(new RefreshEligibility(true, null));
    }

    public void enforceCooldown() {
        RefreshEligibility eligibility = eligibility();
        if (!eligibility.refreshAvailable()) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Refresh available at " + eligibility.nextRefreshAvailableAt()
            );
        }
    }

    private RefreshEligibility eligibilityFrom(Instant generatedAt, Instant now) {
        Instant nextAllowed = generatedAt.plus(refreshCooldown);
        boolean available = !now.isBefore(nextAllowed);
        return new RefreshEligibility(available, available ? null : nextAllowed);
    }

    @Transactional
    public ChallengeListSnapshot refreshAll(Instant now) {
        List<Challenge> challenges = challengeRepository.findAll();
        List<ChallengeSummaryCache> rows = new ArrayList<>(challenges.size());
        List<ChallengeSummaryResponse> summaries = new ArrayList<>(challenges.size());

        for (Challenge challenge : challenges) {
            ChallengeSummaryResponse summary = toSummaryResponse(challenge, now);
            summaries.add(summary);
            rows.add(new ChallengeSummaryCache(challenge.getId(), challenge.getStartAt(), serialize(summary), now));
        }

        summaryCacheRepository.deleteAllInBatch();
        summaryCacheRepository.saveAll(rows);

        ChallengeListCacheState state = stateRepository.findById(ChallengeListCacheState.SINGLETON_ID)
                .orElseGet(() -> new ChallengeListCacheState(now));
        state.updateGeneratedAt(now);
        stateRepository.save(state);

        summaries.sort((a, b) -> b.startAt().compareTo(a.startAt()));
        return new ChallengeListSnapshot(summaries, now);
    }

    /**
     * Safety net: writes/refreshes the cache row for a single challenge (e.g. right after
     * creation) without touching the global {@code generated_at}, so it shows up immediately
     * in the lists without waiting for someone to click "Refresh".
     */
    @Transactional
    public void upsertOne(Challenge challenge, Instant now) {
        ChallengeSummaryResponse summary = toSummaryResponse(challenge, now);
        summaryCacheRepository.save(
                new ChallengeSummaryCache(challenge.getId(), challenge.getStartAt(), serialize(summary), now)
        );
    }

    private String serialize(ChallengeSummaryResponse summary) {
        try {
            return objectMapper.writeValueAsString(summary);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize challenge summary", e);
        }
    }

    private ChallengeSummaryResponse deserialize(ChallengeSummaryCache cache) {
        try {
            return objectMapper.readValue(cache.getPayload(), ChallengeSummaryResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize challenge summary", e);
        }
    }

    private ChallengeSummaryResponse toSummaryResponse(Challenge challenge, Instant now) {
        boolean started = !now.isBefore(challenge.getStartAt());

        if (challenge.getType() == ChallengeType.DUOQ) {
            List<DuoProgressResponse> duos = duoProgressService.buildPreview(challenge);
            boolean allReachedMaxGames = allDuosReachedMaxGames(challenge, started, duos);
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
                    challenge, now, duos.size(), participantGameNames, List.of(), previewDuos, allReachedMaxGames);
        }

        List<ChallengeParticipant> participants = participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId());
        List<String> participantGameNames = participants.stream()
                .map(ChallengeParticipant::getRiotGameName)
                .toList();
        List<ParticipantPreviewResponse> previewParticipants;
        boolean allReachedMaxGames = false;

        if (started) {
            List<ParticipantProgressResponse> fullProgress = progressService.buildPreviewProgress(challenge, participants);
            previewParticipants = fullProgress.stream()
                    .limit(PREVIEW_LIMIT)
                    .map(ParticipantPreviewResponse::from)
                    .toList();
            allReachedMaxGames = allParticipantsReachedMaxGames(challenge, fullProgress);
        } else {
            previewParticipants = new ArrayList<>();
            for (int index = 0; index < Math.min(PREVIEW_LIMIT, participants.size()); index++) {
                previewParticipants.add(
                        ParticipantPreviewResponse.fromParticipant(participants.get(index), index + 1)
                );
            }
        }

        return ChallengeSummaryResponse.from(
                challenge, now, participants.size(), participantGameNames, previewParticipants, List.of(), allReachedMaxGames);
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
}
