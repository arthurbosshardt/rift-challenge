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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeParticipantSyncService {

    static final int MAX_NEW_MATCHES_PER_REFRESH = 10;
    static final int MAX_CATCHUP_MATCHES_PER_REFRESH = 100;
    static final int MAX_HISTORICAL_CATCHUP_MATCHES = 500;
    static final int MAX_POST_CHALLENGE_MATCHES_FOR_RANK_REPLAY = 50;

    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotMatchRepository riotMatchRepository;
    private final ChallengeParticipantMatchRepository participantMatchRepository;
    private final RiotLeagueClient riotLeagueClient;
    private final RiotMatchClient riotMatchClient;
    private final ParticipantProfileService participantProfileService;
    private final ParticipantMatchChampionBackfillService championBackfillService;

    public ChallengeParticipantSyncService(
            RankSnapshotRepository rankSnapshotRepository,
            RiotMatchRepository riotMatchRepository,
            ChallengeParticipantMatchRepository participantMatchRepository,
            RiotLeagueClient riotLeagueClient,
            RiotMatchClient riotMatchClient,
            ParticipantProfileService participantProfileService,
            ParticipantMatchChampionBackfillService championBackfillService
    ) {
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotMatchRepository = riotMatchRepository;
        this.participantMatchRepository = participantMatchRepository;
        this.riotLeagueClient = riotLeagueClient;
        this.riotMatchClient = riotMatchClient;
        this.participantProfileService = participantProfileService;
        this.championBackfillService = championBackfillService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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
        if (currentLeague.isEmpty()) {
            return;
        }

        RankState refreshState = resolveRefreshRankState(challenge, participant, now, currentLeague.get());
        if (refreshState == null) {
            return;
        }

        List<Boolean> challengeWindowWinsNewestFirst = findChallengeWindowWinOutcomesNewestFirst(participant.getId());
        RankState baselineState = RankReplayService.replayBackward(refreshState, challengeWindowWinsNewestFirst);

        Instant refreshAt = challenge.getEndAt() != null && now.isAfter(challenge.getEndAt()) ? challenge.getEndAt() : now;
        int challengeWindowWins = challengeWindowWinsNewestFirst.stream().mapToInt(win -> win ? 1 : 0).sum();
        int challengeWindowLosses = challengeWindowWinsNewestFirst.size() - challengeWindowWins;

        rankSnapshotRepository.deleteByParticipantIdAndSnapshotType(
                participant.getId(),
                RankSnapshot.SnapshotType.BASELINE
        );
        rankSnapshotRepository.deleteByParticipantIdAndSnapshotType(
                participant.getId(),
                RankSnapshot.SnapshotType.REFRESH
        );

        rankSnapshotRepository.save(toSnapshot(
                participant.getId(),
                challenge.getStartAt(),
                RankSnapshot.SnapshotType.BASELINE,
                baselineState,
                0,
                0
        ));

        rankSnapshotRepository.save(toSnapshot(
                participant.getId(),
                refreshAt,
                RankSnapshot.SnapshotType.REFRESH,
                refreshState,
                challengeWindowWins,
                challengeWindowLosses
        ));
    }

    private RankState resolveRefreshRankState(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            RiotLeagueEntryDto currentLeague
    ) {
        RankState currentState = RankReplayService.fromLeague(currentLeague);

        if (challenge.getEndAt() != null && now.isAfter(challenge.getEndAt())) {
            List<Boolean> postChallengeWinsNewestFirst = fetchWinOutcomesBetween(
                    participant,
                    challenge.getEndAt(),
                    now,
                    MAX_POST_CHALLENGE_MATCHES_FOR_RANK_REPLAY
            );
            return RankReplayService.replayBackward(currentState, postChallengeWinsNewestFirst);
        }

        return currentState;
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
        championBackfillService.backfillForParticipant(participant.getId());

        long startEpochSeconds = challenge.getStartAt().getEpochSecond();
        Long endEpochSeconds = challenge.getEndAt() != null ? challenge.getEndAt().getEpochSecond() : null;
        long existingMatches = participantMatchRepository.countByParticipantId(participant.getId());
        int importLimit = resolveImportLimit(challenge, existingMatches);

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                participant.getRiotPuuid(),
                startEpochSeconds,
                endEpochSeconds,
                importLimit
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

            RiotMatchDetailDto match = riotMatchClient.getMatch(matchId);
            Instant gameStart = Instant.ofEpochMilli(match.info().gameStartTimestamp());

            if (gameStart.isBefore(challenge.getStartAt())) {
                continue;
            }
            if (challenge.getEndAt() != null && !gameStart.isBefore(challenge.getEndAt())) {
                continue;
            }
            if (match.info().queueId() != RiotMatchClient.RANKED_SOLO_QUEUE_ID) {
                continue;
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
            imported++;
        }
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

    private List<Boolean> findChallengeWindowWinOutcomesNewestFirst(UUID participantId) {
        List<ChallengeParticipantMatchRepository.ParticipantMatchOutcome> outcomes =
                participantMatchRepository.findOutcomesByParticipantId(participantId);

        return outcomes.stream()
                .map(outcome -> {
                    Instant gameStart = riotMatchRepository.findByRiotMatchId(outcome.getMatchId())
                            .map(RiotMatch::getGameStart)
                            .orElse(Instant.EPOCH);
                    return new MatchOutcome(gameStart, outcome.isWin());
                })
                .sorted(Comparator.comparing(MatchOutcome::gameStart).reversed())
                .map(MatchOutcome::win)
                .toList();
    }

    private List<Boolean> fetchWinOutcomesBetween(
            ChallengeParticipant participant,
            Instant fromInclusive,
            Instant toExclusive,
            int maxMatches
    ) {
        List<MatchOutcome> outcomes = new ArrayList<>();
        long startEpochSeconds = fromInclusive.getEpochSecond();
        Long endEpochSeconds = toExclusive.getEpochSecond();

        List<String> matchIds = riotMatchClient.getAllRankedSoloMatchIdsInWindow(
                participant.getRiotPuuid(),
                startEpochSeconds,
                endEpochSeconds,
                maxMatches
        );

        for (String matchId : matchIds) {
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
        }

        return outcomes.stream()
                .sorted(Comparator.comparing(MatchOutcome::gameStart).reversed())
                .map(MatchOutcome::win)
                .toList();
    }

    private void persistMatchIfNeeded(RiotMatchDetailDto match, Instant gameStart) {
        String matchId = match.metadata().matchId();
        if (!riotMatchRepository.existsByRiotMatchId(matchId)) {
            riotMatchRepository.save(
                    RiotMatch.create(matchId, match.info().queueId(), gameStart)
            );
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

    private RankSnapshot toSnapshot(
            UUID participantId,
            Instant capturedAt,
            RankSnapshot.SnapshotType snapshotType,
            RankState state,
            int wins,
            int losses
    ) {
        return RankSnapshot.create(
                participantId,
                capturedAt,
                snapshotType,
                RiotLeagueClient.RANKED_SOLO_QUEUE,
                state.tier(),
                state.rankDivision(),
                state.leaguePoints(),
                wins,
                losses
        );
    }

    private record MatchOutcome(Instant gameStart, boolean win) {
    }
}
