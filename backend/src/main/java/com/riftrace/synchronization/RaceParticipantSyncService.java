package com.riftrace.synchronization;

import com.riftrace.race.ParticipantProfileService;
import com.riftrace.race.Race;
import com.riftrace.race.RaceParticipant;
import com.riftrace.riot.RankReplayService;
import com.riftrace.riot.RankReplayService.RankState;
import com.riftrace.riot.RiotLeagueClient;
import com.riftrace.riot.RiotMatchClient;
import com.riftrace.riot.dto.RiotLeagueEntryDto;
import com.riftrace.riot.dto.RiotMatchDetailDto;
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
public class RaceParticipantSyncService {

    static final int MAX_NEW_MATCHES_PER_REFRESH = 10;
    static final int MAX_CATCHUP_MATCHES_PER_REFRESH = 100;
    static final int MAX_HISTORICAL_CATCHUP_MATCHES = 500;
    static final int MAX_POST_RACE_MATCHES_FOR_RANK_REPLAY = 50;

    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotMatchRepository riotMatchRepository;
    private final RaceParticipantMatchRepository participantMatchRepository;
    private final RiotLeagueClient riotLeagueClient;
    private final RiotMatchClient riotMatchClient;
    private final ParticipantProfileService participantProfileService;

    public RaceParticipantSyncService(
            RankSnapshotRepository rankSnapshotRepository,
            RiotMatchRepository riotMatchRepository,
            RaceParticipantMatchRepository participantMatchRepository,
            RiotLeagueClient riotLeagueClient,
            RiotMatchClient riotMatchClient,
            ParticipantProfileService participantProfileService
    ) {
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotMatchRepository = riotMatchRepository;
        this.participantMatchRepository = participantMatchRepository;
        this.riotLeagueClient = riotLeagueClient;
        this.riotMatchClient = riotMatchClient;
        this.participantProfileService = participantProfileService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncParticipant(Race race, RaceParticipant participant, Instant now) {
        syncRaceWindowMatches(race, participant);

        if (race.getStartAt().isBefore(now)) {
            ensureHistoricalSnapshots(race, participant, now);
        } else {
            ensureBaselineSnapshot(participant, now);
            captureRefreshSnapshot(participant, now);
        }

        participantProfileService.ensureProfileIcon(participant.getId());
    }

    private void ensureHistoricalSnapshots(Race race, RaceParticipant participant, Instant now) {
        Optional<RiotLeagueEntryDto> currentLeague = riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid());
        if (currentLeague.isEmpty()) {
            return;
        }

        RankState refreshState = resolveRefreshRankState(race, participant, now, currentLeague.get());
        if (refreshState == null) {
            return;
        }

        List<Boolean> raceWindowWinsNewestFirst = findRaceWindowWinOutcomesNewestFirst(participant.getId());
        RankState baselineState = RankReplayService.replayBackward(refreshState, raceWindowWinsNewestFirst);

        Instant refreshAt = race.getEndAt() != null && now.isAfter(race.getEndAt()) ? race.getEndAt() : now;
        int raceWindowWins = raceWindowWinsNewestFirst.stream().mapToInt(win -> win ? 1 : 0).sum();
        int raceWindowLosses = raceWindowWinsNewestFirst.size() - raceWindowWins;

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
                race.getStartAt(),
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
                raceWindowWins,
                raceWindowLosses
        ));
    }

    private RankState resolveRefreshRankState(
            Race race,
            RaceParticipant participant,
            Instant now,
            RiotLeagueEntryDto currentLeague
    ) {
        RankState currentState = RankReplayService.fromLeague(currentLeague);

        if (race.getEndAt() != null && now.isAfter(race.getEndAt())) {
            List<Boolean> postRaceWinsNewestFirst = fetchWinOutcomesBetween(
                    participant,
                    race.getEndAt(),
                    now,
                    MAX_POST_RACE_MATCHES_FOR_RANK_REPLAY
            );
            return RankReplayService.replayBackward(currentState, postRaceWinsNewestFirst);
        }

        return currentState;
    }

    private void ensureBaselineSnapshot(RaceParticipant participant, Instant now) {
        if (hasBaseline(participant.getId())) {
            return;
        }

        riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid())
                .ifPresent(entry -> rankSnapshotRepository.save(
                        toSnapshot(participant.getId(), now, RankSnapshot.SnapshotType.BASELINE, entry)
                ));
    }

    private void captureRefreshSnapshot(RaceParticipant participant, Instant now) {
        riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid())
                .ifPresent(entry -> rankSnapshotRepository.save(
                        toSnapshot(participant.getId(), now, RankSnapshot.SnapshotType.REFRESH, entry)
                ));
    }

    private void syncRaceWindowMatches(Race race, RaceParticipant participant) {
        long startEpochSeconds = race.getStartAt().getEpochSecond();
        Long endEpochSeconds = race.getEndAt() != null ? race.getEndAt().getEpochSecond() : null;
        long existingMatches = participantMatchRepository.countByParticipantId(participant.getId());
        int importLimit = resolveImportLimit(race, existingMatches);

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
                continue;
            }

            RiotMatchDetailDto match = riotMatchClient.getMatch(matchId);
            Instant gameStart = Instant.ofEpochMilli(match.info().gameStartTimestamp());

            if (gameStart.isBefore(race.getStartAt())) {
                continue;
            }
            if (race.getEndAt() != null && !gameStart.isBefore(race.getEndAt())) {
                continue;
            }
            if (match.info().queueId() != RiotMatchClient.RANKED_SOLO_QUEUE_ID) {
                continue;
            }

            persistMatchIfNeeded(match, gameStart);

            boolean win = match.info().participants().stream()
                    .filter(part -> participant.getRiotPuuid().equals(part.puuid()))
                    .findFirst()
                    .map(participantData -> {
                        participantProfileService.updateProfileIconIfMissing(
                                participant.getId(),
                                participantData.profileIcon()
                        );
                        return participantData.win();
                    })
                    .orElse(false);

            participantMatchRepository.save(
                    RaceParticipantMatch.create(race.getId(), participant.getId(), matchId, win)
            );
            imported++;
        }
    }

    private int resolveImportLimit(Race race, long existingMatches) {
        if (existingMatches > 0) {
            return MAX_NEW_MATCHES_PER_REFRESH;
        }
        if (race.getEndAt() != null) {
            return MAX_HISTORICAL_CATCHUP_MATCHES;
        }
        return MAX_CATCHUP_MATCHES_PER_REFRESH;
    }

    private List<Boolean> findRaceWindowWinOutcomesNewestFirst(UUID participantId) {
        List<RaceParticipantMatchRepository.ParticipantMatchOutcome> outcomes =
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
            RaceParticipant participant,
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
