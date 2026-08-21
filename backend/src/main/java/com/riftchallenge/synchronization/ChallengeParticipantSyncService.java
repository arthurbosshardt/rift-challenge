package com.riftchallenge.synchronization;

import com.riftchallenge.challenge.ParticipantProfileService;
import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeDataSyncService;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RankReplayService;
import com.riftchallenge.riot.RankReplayService.RankState;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChallengeParticipantSyncService {

    static final int MAX_NEW_MATCHES_PER_REFRESH = 10;
    static final int MAX_CATCHUP_MATCHES_PER_REFRESH = 25;
    static final int MAX_HISTORICAL_CATCHUP_MATCHES = 25;
    static final int MAX_POST_CHALLENGE_MATCHES_FOR_RANK_REPLAY = 500;

    private static final Logger log = LoggerFactory.getLogger(ChallengeParticipantSyncService.class);

    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotMatchRepository riotMatchRepository;
    private final ChallengeParticipantMatchRepository participantMatchRepository;
    private final RiotLeagueClient riotLeagueClient;
    private final RiotMatchClient riotMatchClient;
    private final RiotMatchLookupService riotMatchLookupService;
    private final ParticipantProfileService participantProfileService;
    private final ParticipantMatchChampionBackfillService championBackfillService;
    private final HistoricalRankSnapshotService historicalRankSnapshotService;
    private final ChallengeDataSyncService dataSyncService;

    public ChallengeParticipantSyncService(
            RankSnapshotRepository rankSnapshotRepository,
            RiotMatchRepository riotMatchRepository,
            ChallengeParticipantMatchRepository participantMatchRepository,
            RiotLeagueClient riotLeagueClient,
            RiotMatchClient riotMatchClient,
            RiotMatchLookupService riotMatchLookupService,
            ParticipantProfileService participantProfileService,
            ParticipantMatchChampionBackfillService championBackfillService,
            HistoricalRankSnapshotService historicalRankSnapshotService,
            ChallengeDataSyncService dataSyncService
    ) {
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotMatchRepository = riotMatchRepository;
        this.participantMatchRepository = participantMatchRepository;
        this.riotLeagueClient = riotLeagueClient;
        this.riotMatchClient = riotMatchClient;
        this.riotMatchLookupService = riotMatchLookupService;
        this.participantProfileService = participantProfileService;
        this.championBackfillService = championBackfillService;
        this.historicalRankSnapshotService = historicalRankSnapshotService;
        this.dataSyncService = dataSyncService;
    }

    public void syncParticipant(Challenge challenge, ChallengeParticipant participant, Instant now) {
        if (shouldSkipFinishedParticipantSync(challenge, participant, now)) {
            if (isChallengeFinished(challenge, now)) {
                ensureMatchBasedHistoricalSnapshots(challenge, participant, now);
            }
            log.info(
                    "Skipping Riot sync for participant {} on finished challenge (data already complete)",
                    participant.getId()
            );
            return;
        }

        boolean dataChanged = false;
        boolean finished = isChallengeFinished(challenge, now);
        boolean hasRankSnapshots = hasBaseline(participant.getId()) && hasRefreshSnapshot(participant.getId());
        boolean skipChampionBackfill = finished
                && participantMatchRepository.countMissingChampionIdByParticipantId(participant.getId()) == 0;

        int importedMatches = syncChallengeWindowMatches(challenge, participant, skipChampionBackfill);
        if (importedMatches > 0) {
            dataChanged = true;
        }

        if (challenge.getStartAt().isBefore(now)) {
            if (needsHistoricalSnapshotRefresh(challenge, participant, now, hasRankSnapshots)) {
                ensureHistoricalSnapshots(challenge, participant, now);
                dataChanged = true;
            }
        } else {
            if (ensureBaselineSnapshot(participant, now, challenge.getRegion())) {
                dataChanged = true;
            }
            if (captureRefreshSnapshot(participant, now, challenge.getRegion())) {
                dataChanged = true;
            }
        }

        participantProfileService.ensureProfileIcon(participant.getId(), challenge.getRegion());

        if (dataChanged) {
            dataSyncService.touchDataSyncedAt(challenge.getId(), now);
        }
    }

    private void ensureHistoricalSnapshots(Challenge challenge, ChallengeParticipant participant, Instant now) {
        if (isChallengeFinished(challenge, now)) {
            ensureMatchBasedHistoricalSnapshots(challenge, participant, now);
            return;
        }

        Optional<RiotLeagueEntryDto> currentLeague =
                riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid(), challenge.getRegion());
        if (currentLeague.isPresent()) {
            ensureHistoricalSnapshotsFromLeague(challenge, participant, now, currentLeague.get());
            return;
        }
    }

    private void ensureHistoricalSnapshotsFromLeague(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            RiotLeagueEntryDto currentLeague
    ) {
        List<Boolean> challengeWindowWinsNewestFirst = findChallengeWindowWinOutcomesNewestFirst(participant);
        if (challengeWindowWinsNewestFirst.isEmpty()) {
            return;
        }

        Optional<RankState> refreshState = resolveRefreshRankState(challenge, participant, now, currentLeague);
        boolean estimated;

        if (refreshState.isEmpty()) {
            List<Boolean> winsOldestFirst = challengeWindowWinsNewestFirst.reversed();
            refreshState = RankReplayService.estimateFromMatches(winsOldestFirst).map(RankReplayService.MatchBasedRankEstimate::refresh);
            estimated = true;
        } else {
            estimated = isChallengeFinished(challenge, now);
        }

        RankState baselineState = RankReplayService.replayBackward(refreshState.get(), challengeWindowWinsNewestFirst);
        int challengeWindowWins = challengeWindowWinsNewestFirst.stream().mapToInt(win -> win ? 1 : 0).sum();
        int challengeWindowLosses = challengeWindowWinsNewestFirst.size() - challengeWindowWins;

        saveHistoricalSnapshots(
                challenge,
                participant,
                now,
                baselineState,
                refreshState.get(),
                challengeWindowWins,
                challengeWindowLosses,
                estimated
        );
    }

    /**
     * Defis termines : rang deduit uniquement des matchs synchronises dans la fenetre.
     * N'ancre jamais sur le classement Riot actuel (ex. Diamant en 2026 pour un defi 2025).
     */
    private void ensureMatchBasedHistoricalSnapshots(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now
    ) {
        if (challenge.getEndAt() == null || !now.isAfter(challenge.getEndAt())) {
            return;
        }

        List<Boolean> winsOldestFirst = findChallengeWindowWinOutcomesOldestFirst(participant);
        if (winsOldestFirst.isEmpty()) {
            return;
        }

        if (winsOldestFirst.size() < RankReplayService.MIN_MATCHES_FOR_RANK_ESTIMATE) {
            rankSnapshotRepository.deleteByParticipantIdAndSnapshotType(
                    participant.getId(),
                    RankSnapshot.SnapshotType.BASELINE
            );
            rankSnapshotRepository.deleteByParticipantIdAndSnapshotType(
                    participant.getId(),
                    RankSnapshot.SnapshotType.REFRESH
            );
            log.info(
                    "Cleared rank snapshots for participant {} ({} matches, below minimum for estimation)",
                    participant.getId(),
                    winsOldestFirst.size()
            );
            return;
        }

        Optional<RankReplayService.MatchBasedRankEstimate> estimate = RankReplayService.estimateFromMatches(winsOldestFirst);
        if (estimate.isEmpty()) {
            return;
        }

        RankState baselineState = estimate.get().baseline();
        RankState refreshState = estimate.get().refresh();
        int challengeWindowWins = (int) winsOldestFirst.stream().filter(Boolean::booleanValue).count();
        int challengeWindowLosses = winsOldestFirst.size() - challengeWindowWins;

        log.info(
                "Estimated historical rank for participant {} from {} challenge matches (end anchor {} {})",
                participant.getId(),
                winsOldestFirst.size(),
                refreshState.tier(),
                refreshState.rankDivision()
        );

        saveHistoricalSnapshots(
                challenge,
                participant,
                now,
                baselineState,
                refreshState,
                challengeWindowWins,
                challengeWindowLosses,
                true
        );
    }

    private void saveHistoricalSnapshots(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            RankState baselineState,
            RankState refreshState,
            int challengeWindowWins,
            int challengeWindowLosses,
            boolean estimated
    ) {
        historicalRankSnapshotService.saveHistoricalSnapshots(
                challenge,
                participant,
                now,
                baselineState,
                refreshState,
                challengeWindowWins,
                challengeWindowLosses,
                estimated
        );
    }

    private Optional<RankState> resolveRefreshRankState(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            RiotLeagueEntryDto currentLeague
    ) {
        RankState currentState = RankReplayService.fromLeague(currentLeague);

        if (challenge.getEndAt() != null && now.isAfter(challenge.getEndAt())) {
            WinOutcomeFetchResult postChallengeOutcomes = fetchWinOutcomesBetween(
                    participant,
                    challenge.getEndAt(),
                    now,
                    MAX_POST_CHALLENGE_MATCHES_FOR_RANK_REPLAY,
                    challenge.getRegion()
            );
            if (!postChallengeOutcomes.complete()) {
                log.warn(
                        "Skipping league-anchored rank for participant {}: post-challenge replay incomplete ({} matches)",
                        participant.getId(),
                        postChallengeOutcomes.winsNewestFirst().size()
                );
                return Optional.empty();
            }
            if (postChallengeOutcomes.winsNewestFirst().isEmpty()) {
                log.info(
                        "Skipping current league rank for participant {} on finished challenge (no post-challenge SoloQ games)",
                        participant.getId()
                );
                return Optional.empty();
            }
            return Optional.of(RankReplayService.replayBackward(
                    currentState,
                    postChallengeOutcomes.winsNewestFirst()
            ));
        }

        return Optional.of(currentState);
    }

    private boolean ensureBaselineSnapshot(ChallengeParticipant participant, Instant now, ChallengeRegion region) {
        if (hasBaseline(participant.getId())) {
            return false;
        }

        return riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid(), region)
                .map(entry -> {
                    rankSnapshotRepository.save(
                            toSnapshot(participant.getId(), now, RankSnapshot.SnapshotType.BASELINE, entry)
                    );
                    return true;
                })
                .orElse(false);
    }

    private boolean captureRefreshSnapshot(ChallengeParticipant participant, Instant now, ChallengeRegion region) {
        return riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid(), region)
                .map(entry -> {
                    rankSnapshotRepository.save(
                            toSnapshot(participant.getId(), now, RankSnapshot.SnapshotType.REFRESH, entry)
                    );
                    return true;
                })
                .orElse(false);
    }

    private int syncChallengeWindowMatches(
            Challenge challenge,
            ChallengeParticipant participant,
            boolean skipChampionBackfill
    ) {
        purgeMatchesOutsideChallengeWindow(participant);
        if (!skipChampionBackfill) {
            championBackfillService.backfillForParticipant(participant.getId());
        }

        long startEpochSeconds = challenge.getStartAt().getEpochSecond();
        Long endEpochSeconds = challenge.getEndAt() != null ? challenge.getEndAt().getEpochSecond() : null;
        long existingMatches = participantMatchRepository.countByParticipantId(participant.getId());
        int importLimit = resolveImportLimit(challenge, existingMatches);
        int matchIdFetchLimit = resolveMatchIdFetchLimit(existingMatches, importLimit);

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                participant.getRiotPuuid(),
                startEpochSeconds,
                endEpochSeconds,
                matchIdFetchLimit,
                challenge.getRegion()
        );

        int imported = 0;
        for (String matchId : matchIds) {
            if (imported >= importLimit) {
                break;
            }
            if (participantMatchRepository.existsByParticipantIdAndRiotMatchId(participant.getId(), matchId)) {
                championBackfillService.backfillMatchIfMissing(participant, matchId);
                continue;
            }

            try {
                if (!importMatchDetail(challenge, participant, matchId)) {
                    continue;
                }
                imported++;
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    log.warn(
                            "Riot API rate limit while importing matches for participant {} after {} new matches",
                            participant.getId(),
                            imported
                    );
                    break;
                }
                throw exception;
            }
        }

        return imported;
    }

    private boolean importMatchDetail(Challenge challenge, ChallengeParticipant participant, String matchId) {
        RiotMatchDetailDto match = riotMatchLookupService.getMatch(matchId);
        Instant gameStart = Instant.ofEpochMilli(match.info().gameStartTimestamp());

        if (gameStart.isBefore(challenge.getStartAt())) {
            return false;
        }
        if (challenge.getEndAt() != null && !gameStart.isBefore(challenge.getEndAt())) {
            return false;
        }
        if (match.info().queueId() != RiotMatchClient.RANKED_SOLO_QUEUE_ID) {
            return false;
        }

        persistMatchIfNeeded(match, gameStart);

        boolean win = false;
        Integer championId = null;
        for (RiotMatchDetailDto.Participant part : match.info().participants()) {
            if (!participant.getRiotPuuid().equals(part.puuid())) {
                continue;
            }
            participantProfileService.updateProfileIconIfMissing(
                    participant.getId(),
                    part.profileIcon()
            );
            win = part.win();
            championId = part.championId();
            break;
        }

        if (championId == null) {
            championId = ParticipantMatchChampionBackfillService.extractChampionId(
                    match,
                    participant.getRiotPuuid()
            );
        }

        participantMatchRepository.save(
                ChallengeParticipantMatch.create(challenge.getId(), participant.getId(), matchId, win, championId)
        );
        return true;
    }

    private void purgeMatchesOutsideChallengeWindow(ChallengeParticipant participant) {
        List<ChallengeParticipantMatch> staleMatches = participantMatchRepository.findOutsideChallengeWindow(
                participant.getId(),
                participant.getChallengeId()
        );
        if (staleMatches.isEmpty()) {
            return;
        }

        participantMatchRepository.deleteAll(staleMatches);
        log.info(
                "Removed {} out-of-window matches for participant {}",
                staleMatches.size(),
                participant.getId()
        );
    }

    private int resolveImportLimit(Challenge challenge, long existingMatches) {
        if (existingMatches > 0) {
            return MAX_NEW_MATCHES_PER_REFRESH;
        }
        if (challenge.getEndAt() != null) {
            return MAX_HISTORICAL_CATCHUP_MATCHES;
        }
        return MAX_CATCHUP_MATCHES_PER_REFRESH;
    }

    private int resolveMatchIdFetchLimit(long existingMatches, int importLimit) {
        if (existingMatches == 0) {
            return importLimit;
        }

        return (int) Math.min(100, existingMatches + importLimit);
    }

    private List<Boolean> findChallengeWindowWinOutcomesNewestFirst(ChallengeParticipant participant) {
        return participantMatchRepository.findOutcomesInChallengeWindow(
                        participant.getId(),
                        participant.getChallengeId()
                ).stream()
                .map(ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindow::isWin)
                .toList();
    }

    private List<Boolean> findChallengeWindowWinOutcomesOldestFirst(ChallengeParticipant participant) {
        return findChallengeWindowWinOutcomesNewestFirst(participant).reversed();
    }

    private WinOutcomeFetchResult fetchWinOutcomesBetween(
            ChallengeParticipant participant,
            Instant fromInclusive,
            Instant toExclusive,
            int maxMatches,
            ChallengeRegion region
    ) {
        List<MatchOutcome> outcomes = new ArrayList<>();
        long startEpochSeconds = fromInclusive.getEpochSecond();
        Long endEpochSeconds = toExclusive.getEpochSecond();
        boolean rateLimited = false;

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                participant.getRiotPuuid(),
                startEpochSeconds,
                endEpochSeconds,
                maxMatches,
                region
        );

        if (matchIds.size() >= maxMatches) {
            log.warn(
                    "Post-challenge replay capped at {} matches for participant {}",
                    maxMatches,
                    participant.getId()
            );
            return new WinOutcomeFetchResult(List.of(), false);
        }

        for (String matchId : matchIds) {
            try {
                RiotMatchDetailDto match = riotMatchLookupService.getMatch(matchId);
                Instant gameStart = Instant.ofEpochMilli(match.info().gameStartTimestamp());

                if (gameStart.isBefore(fromInclusive) || !gameStart.isBefore(toExclusive)) {
                    continue;
                }
                if (match.info().queueId() != RiotMatchClient.RANKED_SOLO_QUEUE_ID) {
                    continue;
                }

                boolean win = match.info().participants().stream()
                        .filter(part -> participant.getRiotPuuid().equals(part.puuid()))
                        .findFirst()
                        .map(RiotMatchDetailDto.Participant::win)
                        .orElse(false);
                outcomes.add(new MatchOutcome(gameStart, win));
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    rateLimited = true;
                    break;
                }
                throw exception;
            }
        }

        List<Boolean> winsNewestFirst = outcomes.stream()
                .sorted(Comparator.comparing(MatchOutcome::gameStart).reversed())
                .map(MatchOutcome::win)
                .toList();

        return new WinOutcomeFetchResult(winsNewestFirst, !rateLimited);
    }

    private void persistMatchIfNeeded(RiotMatchDetailDto match, Instant gameStart) {
        String matchId = match.metadata().matchId();
        if (riotMatchRepository.existsByRiotMatchId(matchId)) {
            return;
        }

        try {
            riotMatchRepository.saveAndFlush(
                    RiotMatch.create(matchId, match.info().queueId(), gameStart)
            );
        } catch (DataIntegrityViolationException exception) {
            log.debug("Riot match {} already persisted by another participant", matchId);
        }
    }

    private boolean hasBaseline(UUID participantId) {
        return rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                        participantId,
                        RankSnapshot.SnapshotType.BASELINE
                )
                .isPresent();
    }

    private boolean hasRefreshSnapshot(UUID participantId) {
        return rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                        participantId,
                        RankSnapshot.SnapshotType.REFRESH
                )
                .isPresent();
    }

    private boolean isRefreshSnapshotEstimated(UUID participantId) {
        return rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                        participantId,
                        RankSnapshot.SnapshotType.REFRESH
                )
                .map(RankSnapshot::isEstimated)
                .orElse(false);
    }

    private boolean needsHistoricalSnapshotRefresh(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            boolean hasRankSnapshots
    ) {
        if (!isChallengeFinished(challenge, now)) {
            return !hasRankSnapshots;
        }
        return true;
    }

    private boolean isChallengeFinished(Challenge challenge, Instant now) {
        return challenge.getEndAt() != null && now.isAfter(challenge.getEndAt());
    }

    private boolean shouldSkipFinishedParticipantSync(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now
    ) {
        if (!isChallengeFinished(challenge, now)) {
            return false;
        }
        if (!hasBaseline(participant.getId()) || !hasRefreshSnapshot(participant.getId())) {
            return false;
        }
        if (isRefreshSnapshotEstimated(participant.getId())) {
            return false;
        }
        if (participantMatchRepository.countByParticipantId(participant.getId()) == 0) {
            return false;
        }
        if (participantMatchRepository.countMissingChampionIdByParticipantId(participant.getId()) > 0) {
            return false;
        }
        return !hasPendingMatchImports(challenge, participant);
    }

    private boolean hasPendingMatchImports(Challenge challenge, ChallengeParticipant participant) {
        long existingMatches = participantMatchRepository.countByParticipantId(participant.getId());
        int importLimit = resolveImportLimit(challenge, existingMatches);
        int matchIdFetchLimit = resolveMatchIdFetchLimit(existingMatches, importLimit);

        long startEpochSeconds = challenge.getStartAt().getEpochSecond();
        Long endEpochSeconds = challenge.getEndAt() != null ? challenge.getEndAt().getEpochSecond() : null;

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                participant.getRiotPuuid(),
                startEpochSeconds,
                endEpochSeconds,
                matchIdFetchLimit,
                challenge.getRegion()
        );

        for (String matchId : matchIds) {
            if (!participantMatchRepository.existsByParticipantIdAndRiotMatchId(participant.getId(), matchId)) {
                return true;
            }
        }

        return false;
    }

    private RankSnapshot toSnapshot(
            UUID participantId,
            Instant capturedAt,
            RankSnapshot.SnapshotType snapshotType,
            RiotLeagueEntryDto entry
    ) {
        return RankSnapshot.create(
                participantId,
                capturedAt,
                snapshotType,
                entry.queueType(),
                entry.tier(),
                entry.rank(),
                entry.leaguePoints(),
                entry.wins(),
                entry.losses()
        );
    }

    private record MatchOutcome(Instant gameStart, boolean win) {
    }

    private record WinOutcomeFetchResult(List<Boolean> winsNewestFirst, boolean complete) {
    }
}
