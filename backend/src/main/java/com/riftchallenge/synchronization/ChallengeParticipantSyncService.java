package com.riftchallenge.synchronization;

import com.riftchallenge.challenge.ParticipantProfileService;
import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.riot.RankReplayService;
import com.riftchallenge.riot.RankReplayService.RankState;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.RiotMatchClient;
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
    static final RankState UNRANKED_MATCH_ONLY_BASELINE = new RankState("IRON", "IV", 0);

    private static final Logger log = LoggerFactory.getLogger(ChallengeParticipantSyncService.class);

    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotMatchRepository riotMatchRepository;
    private final ChallengeParticipantMatchRepository participantMatchRepository;
    private final RiotLeagueClient riotLeagueClient;
    private final RiotMatchClient riotMatchClient;
    private final ParticipantProfileService participantProfileService;
    private final ParticipantMatchChampionBackfillService championBackfillService;
    private final HistoricalRankSnapshotService historicalRankSnapshotService;

    public ChallengeParticipantSyncService(
            RankSnapshotRepository rankSnapshotRepository,
            RiotMatchRepository riotMatchRepository,
            ChallengeParticipantMatchRepository participantMatchRepository,
            RiotLeagueClient riotLeagueClient,
            RiotMatchClient riotMatchClient,
            ParticipantProfileService participantProfileService,
            ParticipantMatchChampionBackfillService championBackfillService,
            HistoricalRankSnapshotService historicalRankSnapshotService
    ) {
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotMatchRepository = riotMatchRepository;
        this.participantMatchRepository = participantMatchRepository;
        this.riotLeagueClient = riotLeagueClient;
        this.riotMatchClient = riotMatchClient;
        this.participantProfileService = participantProfileService;
        this.championBackfillService = championBackfillService;
        this.historicalRankSnapshotService = historicalRankSnapshotService;
    }

    public void syncParticipant(Challenge challenge, ChallengeParticipant participant, Instant now) {
        syncChallengeWindowMatches(challenge, participant);

        if (challenge.getStartAt().isBefore(now)) {
            ensureHistoricalSnapshots(challenge, participant, now);
        } else {
            ensureBaselineSnapshot(participant, now);
            captureRefreshSnapshot(participant, now);
        }

        participantProfileService.ensureProfileIcon(participant.getId());
    }

    private void ensureHistoricalSnapshots(Challenge challenge, ChallengeParticipant participant, Instant now) {
        Optional<RiotLeagueEntryDto> currentLeague = riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid());
        if (currentLeague.isPresent()) {
            ensureHistoricalSnapshotsFromLeague(challenge, participant, now, currentLeague.get());
            return;
        }

        ensureEstimatedSnapshotsForUnrankedParticipant(challenge, participant, now);
    }

    private void ensureHistoricalSnapshotsFromLeague(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            RiotLeagueEntryDto currentLeague
    ) {
        Optional<RankState> refreshState = resolveRefreshRankState(challenge, participant, now, currentLeague);
        if (refreshState.isEmpty()) {
            return;
        }

        List<Boolean> challengeWindowWinsNewestFirst = findChallengeWindowWinOutcomesNewestFirst(participant);
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
                challengeWindowLosses
        );
    }

    private void ensureEstimatedSnapshotsForUnrankedParticipant(
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

        RankState baselineState = UNRANKED_MATCH_ONLY_BASELINE;
        RankState refreshState = RankReplayService.replayForward(baselineState, winsOldestFirst);
        int challengeWindowWins = (int) winsOldestFirst.stream().filter(Boolean::booleanValue).count();
        int challengeWindowLosses = winsOldestFirst.size() - challengeWindowWins;

        log.info(
                "Estimated historical rank for unranked participant {} from {} challenge matches",
                participant.getId(),
                winsOldestFirst.size()
        );

        saveHistoricalSnapshots(
                challenge,
                participant,
                now,
                baselineState,
                refreshState,
                challengeWindowWins,
                challengeWindowLosses
        );
    }

    private void saveHistoricalSnapshots(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            RankState baselineState,
            RankState refreshState,
            int challengeWindowWins,
            int challengeWindowLosses
    ) {
        historicalRankSnapshotService.saveHistoricalSnapshots(
                challenge,
                participant,
                now,
                baselineState,
                refreshState,
                challengeWindowWins,
                challengeWindowLosses
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
                    MAX_POST_CHALLENGE_MATCHES_FOR_RANK_REPLAY
            );
            if (!postChallengeOutcomes.complete()) {
                log.warn(
                        "Skipping historical rank snapshot for participant {}: post-challenge replay incomplete ({} matches)",
                        participant.getId(),
                        postChallengeOutcomes.winsNewestFirst().size()
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

    private void ensureBaselineSnapshot(ChallengeParticipant participant, Instant now) {
        if (hasBaseline(participant.getId())) {
            return;
        }

        riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid())
                .ifPresent(entry -> rankSnapshotRepository.save(
                        toSnapshot(participant.getId(), now, RankSnapshot.SnapshotType.BASELINE, entry)
                ));
    }

    private void captureRefreshSnapshot(ChallengeParticipant participant, Instant now) {
        riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid())
                .ifPresent(entry -> rankSnapshotRepository.save(
                        toSnapshot(participant.getId(), now, RankSnapshot.SnapshotType.REFRESH, entry)
                ));
    }

    private void syncChallengeWindowMatches(Challenge challenge, ChallengeParticipant participant) {
        purgeMatchesOutsideChallengeWindow(participant);
        championBackfillService.backfillForParticipant(participant.getId());

        long startEpochSeconds = challenge.getStartAt().getEpochSecond();
        Long endEpochSeconds = challenge.getEndAt() != null ? challenge.getEndAt().getEpochSecond() : null;
        long existingMatches = participantMatchRepository.countByParticipantId(participant.getId());
        int importLimit = resolveImportLimit(challenge, existingMatches);
        int matchIdFetchLimit = resolveMatchIdFetchLimit(existingMatches, importLimit);

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                participant.getRiotPuuid(),
                startEpochSeconds,
                endEpochSeconds,
                matchIdFetchLimit
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
    }

    private boolean importMatchDetail(Challenge challenge, ChallengeParticipant participant, String matchId) {
        RiotMatchDetailDto match = riotMatchClient.getMatch(matchId);
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
            int maxMatches
    ) {
        List<MatchOutcome> outcomes = new ArrayList<>();
        long startEpochSeconds = fromInclusive.getEpochSecond();
        Long endEpochSeconds = toExclusive.getEpochSecond();
        boolean rateLimited = false;

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                participant.getRiotPuuid(),
                startEpochSeconds,
                endEpochSeconds,
                maxMatches
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
                RiotMatchDetailDto match = riotMatchClient.getMatch(matchId);
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
