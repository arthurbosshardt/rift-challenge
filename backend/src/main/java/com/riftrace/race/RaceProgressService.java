package com.riftrace.race;

import com.riftrace.race.dto.ParticipantProgressResponse;
import com.riftrace.riot.MatchLpEstimator;
import com.riftrace.riot.RankScoreConverter;
import com.riftrace.synchronization.RankSnapshot;
import com.riftrace.synchronization.RankSnapshot.SnapshotType;
import com.riftrace.synchronization.RankSnapshotRepository;
import com.riftrace.synchronization.RaceParticipantMatchRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RaceProgressService {

    private final RankSnapshotRepository rankSnapshotRepository;
    private final RaceParticipantMatchRepository participantMatchRepository;

    public RaceProgressService(
            RankSnapshotRepository rankSnapshotRepository,
            RaceParticipantMatchRepository participantMatchRepository
    ) {
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.participantMatchRepository = participantMatchRepository;
    }

    @Transactional(readOnly = true)
    public List<ParticipantProgressResponse> buildProgress(List<RaceParticipant> participants) {
        List<ParticipantProgressResponse> unsorted = new ArrayList<>();

        for (RaceParticipant participant : participants) {
            unsorted.add(buildForParticipant(participant));
        }

        unsorted.sort(Comparator.comparingInt(ParticipantProgressResponse::rankScore).reversed());

        List<ParticipantProgressResponse> ranked = new ArrayList<>();
        for (int index = 0; index < unsorted.size(); index++) {
            ranked.add(unsorted.get(index).withPosition(index + 1));
        }
        return ranked;
    }

    @Transactional(readOnly = true)
    public ParticipantProgressResponse buildForParticipant(RaceParticipant participant) {
        UUID participantId = participant.getId();

        RankSnapshot baseline = rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(participantId, SnapshotType.BASELINE)
                .orElse(null);
        RankSnapshot current = rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(participantId, SnapshotType.REFRESH)
                .orElse(baseline);

        long syncedWins = participantMatchRepository.countWinsByParticipantId(participantId);
        long syncedLosses = participantMatchRepository.countLossesByParticipantId(participantId);

        long wins;
        long losses;
        if (syncedWins + syncedLosses > 0) {
            wins = syncedWins;
            losses = syncedLosses;
        } else {
            wins = resolveSnapshotStat(baseline, current, true);
            losses = resolveSnapshotStat(baseline, current, false);
        }

        if (current == null) {
            return ParticipantProgressResponse.withoutRankData(
                    participant,
                    (int) wins,
                    (int) losses
            );
        }

        int lpGained = computeLpGained(baseline, current, (int) wins, (int) losses);
        int rankScore = RankScoreConverter.toScore(
                current.getTier(),
                current.getRankDivision(),
                current.getLeaguePoints()
        );

        return ParticipantProgressResponse.withRankData(
                participant,
                0,
                current.getTier(),
                current.getRankDivision(),
                current.getLeaguePoints(),
                lpGained,
                rankScore,
                (int) wins,
                (int) losses
        );
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

        if (isRaceWindowSnapshotPair(baseline, current)) {
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

    private static boolean isRaceWindowSnapshotPair(RankSnapshot baseline, RankSnapshot current) {
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
