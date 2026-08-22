package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.riot.MatchLpEstimator;
import com.riftchallenge.riot.RankReplayService;
import com.riftchallenge.riot.RankReplayService.MatchBasedRankEstimate;
import com.riftchallenge.riot.RankReplayService.RankState;
import com.riftchallenge.riot.RankScoreConverter;
import com.riftchallenge.synchronization.RankSnapshot;
import com.riftchallenge.synchronization.RankSnapshot.SnapshotType;
import com.riftchallenge.synchronization.RankSnapshotRepository;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeProgressService {

    static final int MIN_MATCHES_FOR_RANK_ESTIMATE = RankReplayService.MIN_MATCHES_FOR_RANK_ESTIMATE;

    private final RankSnapshotRepository rankSnapshotRepository;
    private final ChallengeParticipantMatchRepository participantMatchRepository;
    private final MatchHistoryService matchHistoryService;
    private final ChallengeRepository challengeRepository;

    public ChallengeProgressService(
            RankSnapshotRepository rankSnapshotRepository,
            ChallengeParticipantMatchRepository participantMatchRepository,
            MatchHistoryService matchHistoryService,
            ChallengeRepository challengeRepository
    ) {
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.participantMatchRepository = participantMatchRepository;
        this.matchHistoryService = matchHistoryService;
        this.challengeRepository = challengeRepository;
    }

    @Transactional(readOnly = true)
    public List<ParticipantProgressResponse> buildProgress(Challenge challenge, List<ChallengeParticipant> participants) {
        return buildProgress(challenge, participants, true);
    }

    @Transactional(readOnly = true)
    public List<ParticipantProgressResponse> buildPreviewProgress(Challenge challenge, List<ChallengeParticipant> participants) {
        return buildProgress(challenge, participants, false);
    }

    private List<ParticipantProgressResponse> buildProgress(
            Challenge challenge,
            List<ChallengeParticipant> participants,
            boolean includeMatchHistory
    ) {
        if (participants.isEmpty()) {
            return List.of();
        }

        UUID challengeId = participants.get(0).getChallengeId();
        List<UUID> participantIds = participants.stream().map(ChallengeParticipant::getId).toList();

        Map<UUID, RankSnapshot> baselineByParticipant = latestSnapshotByParticipant(
                rankSnapshotRepository.findByParticipantIdInAndSnapshotType(participantIds, SnapshotType.BASELINE)
        );
        Map<UUID, RankSnapshot> refreshByParticipant = latestSnapshotByParticipant(
                rankSnapshotRepository.findByParticipantIdInAndSnapshotType(participantIds, SnapshotType.REFRESH)
        );
        Map<UUID, ParticipantWinLoss> winLossByParticipant = challenge.getMaxGames() != null
                ? winLossByParticipantCappedToMaxGames(participantIds, challengeId, challenge.getMaxGames())
                : participantMatchRepository
                        .countWinsAndLossesInChallengeWindowForParticipants(participantIds, challengeId)
                        .stream()
                        .collect(Collectors.toMap(
                                ChallengeParticipantMatchRepository.ParticipantWinLossCount::getParticipantId,
                                row -> new ParticipantWinLoss(row.getWins(), row.getLosses())
                        ));

        List<ParticipantProgressResponse> unsorted = new ArrayList<>();
        for (ChallengeParticipant participant : participants) {
            RankSnapshot baseline = baselineByParticipant.get(participant.getId());
            RankSnapshot current = refreshByParticipant.getOrDefault(participant.getId(), baseline);
            ParticipantWinLoss winLoss = winLossByParticipant.getOrDefault(participant.getId(), ParticipantWinLoss.NONE);
            unsorted.add(buildForParticipant(participant, includeMatchHistory, challenge, baseline, current, winLoss));
        }

        unsorted.sort(ChallengeLeaderboardOrdering.BY_LP_GAIN);

        List<ParticipantProgressResponse> ranked = new ArrayList<>();
        for (int index = 0; index < unsorted.size(); index++) {
            ranked.add(unsorted.get(index).withPosition(index + 1));
        }
        return ranked;
    }

    private Map<UUID, ParticipantWinLoss> winLossByParticipantCappedToMaxGames(
            List<UUID> participantIds,
            UUID challengeId,
            int maxGames
    ) {
        Map<UUID, List<ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindowForParticipant>> outcomesByParticipant =
                participantMatchRepository.findOutcomesInChallengeWindowForParticipants(participantIds, challengeId).stream()
                        .collect(Collectors.groupingBy(
                                ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindowForParticipant::getParticipantId
                        ));

        Map<UUID, ParticipantWinLoss> result = new HashMap<>();
        for (Map.Entry<UUID, List<ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindowForParticipant>> entry
                : outcomesByParticipant.entrySet()) {
            List<ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindowForParticipant> capped = MaxGamesCap.capToOldest(
                    entry.getValue(),
                    maxGames,
                    ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindowForParticipant::getGameStart
            );
            result.put(entry.getKey(), countWinLoss(capped, ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindowForParticipant::isWin));
        }
        return result;
    }

    private static <T> ParticipantWinLoss countWinLoss(List<T> rows, java.util.function.Predicate<T> isWin) {
        long wins = rows.stream().filter(isWin).count();
        long losses = rows.size() - wins;
        return new ParticipantWinLoss(wins, losses);
    }

    private static Map<UUID, RankSnapshot> latestSnapshotByParticipant(List<RankSnapshot> snapshots) {
        Map<UUID, RankSnapshot> latest = new HashMap<>();
        for (RankSnapshot snapshot : snapshots) {
            latest.merge(snapshot.getParticipantId(), snapshot, (existing, candidate) ->
                    candidate.getCapturedAt().isAfter(existing.getCapturedAt()) ? candidate : existing);
        }
        return latest;
    }

    @Transactional(readOnly = true)
    public ParticipantProgressResponse buildForParticipant(ChallengeParticipant participant) {
        return buildForParticipant(participant, true);
    }

    @Transactional(readOnly = true)
    public ParticipantProgressResponse buildForParticipant(
            ChallengeParticipant participant,
            boolean includeMatchHistory
    ) {
        Challenge challenge = challengeRepository.findById(participant.getChallengeId()).orElse(null);
        return buildForParticipant(participant, includeMatchHistory, challenge);
    }

    ParticipantProgressResponse buildForParticipant(
            ChallengeParticipant participant,
            boolean includeMatchHistory,
            Challenge challenge
    ) {
        UUID participantId = participant.getId();
        UUID challengeId = participant.getChallengeId();

        RankSnapshot baseline = rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(participantId, SnapshotType.BASELINE)
                .orElse(null);
        RankSnapshot current = rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(participantId, SnapshotType.REFRESH)
                .orElse(baseline);

        ParticipantWinLoss syncedWinLoss;
        if (challenge != null && challenge.getMaxGames() != null) {
            List<ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindow> capped = MaxGamesCap.capToOldest(
                    participantMatchRepository.findOutcomesInChallengeWindow(participantId, challengeId),
                    challenge.getMaxGames(),
                    ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindow::getGameStart
            );
            syncedWinLoss = countWinLoss(capped, ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindow::isWin);
        } else {
            long syncedWins = participantMatchRepository.countWinsInChallengeWindow(participantId, challengeId);
            long syncedLosses = participantMatchRepository.countLossesInChallengeWindow(participantId, challengeId);
            syncedWinLoss = new ParticipantWinLoss(syncedWins, syncedLosses);
        }

        return buildForParticipant(
                participant,
                includeMatchHistory,
                challenge,
                baseline,
                current,
                syncedWinLoss
        );
    }

    private ParticipantProgressResponse buildForParticipant(
            ChallengeParticipant participant,
            boolean includeMatchHistory,
            Challenge challenge,
            RankSnapshot baseline,
            RankSnapshot current,
            ParticipantWinLoss syncedWinLoss
    ) {
        UUID participantId = participant.getId();
        UUID challengeId = participant.getChallengeId();

        long wins;
        long losses;
        if (syncedWinLoss.wins() + syncedWinLoss.losses() > 0) {
            wins = syncedWinLoss.wins();
            losses = syncedWinLoss.losses();
        } else {
            wins = resolveSnapshotStat(baseline, current, true);
            losses = resolveSnapshotStat(baseline, current, false);
        }

        boolean challengeFinished = isChallengeFinished(challenge);
        int totalGames = (int) (wins + losses);

        if (current == null || (challengeFinished && totalGames < MIN_MATCHES_FOR_RANK_ESTIMATE)) {
            ParticipantProgressResponse progress = ParticipantProgressResponse.withoutRankData(
                    participant,
                    (int) wins,
                    (int) losses
            );
            return includeMatchHistory ? attachMatchHistory(participant, progress, challenge) : progress;
        }

        RankSnapshot effectiveBaseline = baseline;
        RankSnapshot effectiveCurrent = current;
        boolean rankEstimated = current.isEstimated();
        if (totalGames > 0 && (current.isEstimated() || challengeFinished)) {
            Optional<MatchBasedRankEstimate> liveEstimate = estimateRankFromSyncedMatches(participantId, challengeId, challenge);
            if (liveEstimate.isPresent()) {
                effectiveBaseline = toEstimatedSnapshot(participantId, RankSnapshot.SnapshotType.BASELINE, liveEstimate.get().baseline());
                effectiveCurrent = toEstimatedSnapshot(participantId, RankSnapshot.SnapshotType.REFRESH, liveEstimate.get().refresh());
                rankEstimated = true;
            }
        }

        int lpGained = computeLpGained(effectiveBaseline, effectiveCurrent, (int) wins, (int) losses);
        int rankScore = RankScoreConverter.toScore(
                effectiveCurrent.getTier(),
                effectiveCurrent.getRankDivision(),
                effectiveCurrent.getLeaguePoints()
        );

        ParticipantProgressResponse progress = ParticipantProgressResponse.withRankData(
                participant,
                0,
                effectiveCurrent.getTier(),
                effectiveCurrent.getRankDivision(),
                effectiveCurrent.getLeaguePoints(),
                lpGained,
                rankScore,
                (int) wins,
                (int) losses,
                rankEstimated
        );
        return includeMatchHistory ? attachMatchHistory(participant, progress, challenge) : progress;
    }

    private static boolean isChallengeFinished(Challenge challenge) {
        return challenge != null
                && challenge.getEndAt() != null
                && java.time.Instant.now().isAfter(challenge.getEndAt());
    }

    private record ParticipantWinLoss(long wins, long losses) {
        static final ParticipantWinLoss NONE = new ParticipantWinLoss(0, 0);
    }

    private Optional<MatchBasedRankEstimate> estimateRankFromSyncedMatches(UUID participantId, UUID challengeId, Challenge challenge) {
        List<ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindow> outcomes = MaxGamesCap.capToOldest(
                participantMatchRepository.findOutcomesInChallengeWindow(participantId, challengeId),
                challenge == null ? null : challenge.getMaxGames(),
                ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindow::getGameStart
        );
        List<Boolean> winsOldestFirst = outcomes.stream()
                .map(ChallengeParticipantMatchRepository.ParticipantMatchOutcomeInWindow::isWin)
                .toList()
                .reversed();

        return RankReplayService.estimateFromMatches(winsOldestFirst);
    }

    private static RankSnapshot toEstimatedSnapshot(
            UUID participantId,
            RankSnapshot.SnapshotType snapshotType,
            RankState state
    ) {
        return RankSnapshot.create(
                participantId,
                java.time.Instant.EPOCH,
                snapshotType,
                "RANKED_SOLO_5x5",
                state.tier(),
                state.rankDivision(),
                state.leaguePoints(),
                0,
                0,
                true
        );
    }

    private ParticipantProgressResponse attachMatchHistory(
            ChallengeParticipant participant,
            ParticipantProgressResponse progress,
            Challenge challenge
    ) {
        return progress.withRecentMatches(matchHistoryService.buildForParticipant(participant, progress, challenge));
    }

    private int computeLpGained(RankSnapshot baseline, RankSnapshot current, int wins, int losses) {
        if (baseline != null && !sameRank(baseline, current)) {
            return RankScoreConverter.lpGained(
                    baseline.getTier(),
                    baseline.getRankDivision(),
                    baseline.getLeaguePoints(),
                    current.getTier(),
                    current.getRankDivision(),
                    current.getLeaguePoints()
            );
        }

        if (baseline != null && sameRank(baseline, current) && wins + losses > 0) {
            return MatchLpEstimator.estimateLpGainedFromMatches(wins, losses, current.getTier());
        }

        if (baseline != null) {
            return RankScoreConverter.lpGained(
                    baseline.getTier(),
                    baseline.getRankDivision(),
                    baseline.getLeaguePoints(),
                    current.getTier(),
                    current.getRankDivision(),
                    current.getLeaguePoints()
            );
        }

        if (wins + losses > 0) {
            return MatchLpEstimator.estimateLpGainedFromMatches(wins, losses, current.getTier());
        }

        return 0;
    }

    private static boolean sameRank(RankSnapshot left, RankSnapshot right) {
        return Objects.equals(left.getTier(), right.getTier())
                && Objects.equals(left.getRankDivision(), right.getRankDivision())
                && left.getLeaguePoints() == right.getLeaguePoints();
    }

    private static long resolveSnapshotStat(RankSnapshot baseline, RankSnapshot current, boolean wins) {
        if (current == null) {
            return 0;
        }

        if (isChallengeWindowSnapshotPair(baseline, current)) {
            return wins ? nullSafe(current.getWins()) : nullSafe(current.getLosses());
        }

        if (isSameSnapshot(baseline, current)) {
            int total = nullSafe(current.getWins()) + nullSafe(current.getLosses());
            if (total > 0) {
                return wins ? nullSafe(current.getWins()) : nullSafe(current.getLosses());
            }
        }

        return leagueStatDelta(baseline, current, wins);
    }

    private static boolean isChallengeWindowSnapshotPair(RankSnapshot baseline, RankSnapshot current) {
        if (baseline == null) {
            return false;
        }
        int baselineTotal = nullSafe(baseline.getWins()) + nullSafe(baseline.getLosses());
        int refreshTotal = nullSafe(current.getWins()) + nullSafe(current.getLosses());
        return baselineTotal == 0 && refreshTotal > 0;
    }

    private static boolean isSameSnapshot(RankSnapshot baseline, RankSnapshot current) {
        return baseline != null && baseline == current;
    }

    private static int nullSafe(Integer value) {
        return value == null ? 0 : value;
    }

    private static long leagueStatDelta(RankSnapshot baseline, RankSnapshot current, boolean wins) {
        if (current == null) {
            return 0;
        }

        Integer currentValue = wins ? current.getWins() : current.getLosses();
        if (currentValue == null || currentValue <= 0) {
            return 0;
        }

        Integer currentLosses = current.getLosses();
        if (baseline == null || baseline.getWins() == null || baseline.getLosses() == null) {
            if (wins) {
                return currentValue;
            }
            return currentLosses == null ? 0 : currentLosses;
        }

        Integer baselineValue = wins ? baseline.getWins() : baseline.getLosses();
        if (baselineValue == null) {
            return wins ? currentValue : current.getLosses();
        }

        return Math.max(0, currentValue - baselineValue);
    }
}
