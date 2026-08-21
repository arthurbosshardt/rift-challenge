package com.riftchallenge.leaderboard;

import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.leaderboard.dto.LeaderboardEntryResponse;
import com.riftchallenge.leaderboard.dto.LeaderboardMatchHistoryResponse;
import com.riftchallenge.leaderboard.dto.LeaderboardSnapshot;
import com.riftchallenge.leaderboard.dto.LeaderboardWindow;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.MatchLpEstimator;
import com.riftchallenge.riot.RankReplayService;
import com.riftchallenge.riot.RankScoreConverter;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository;
import com.riftchallenge.synchronization.ChallengeParticipantMatchRepository.ParticipantMatchHistorySince;
import com.riftchallenge.synchronization.RankSnapshot;
import com.riftchallenge.synchronization.RankSnapshot.SnapshotType;
import com.riftchallenge.synchronization.RankSnapshotRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Computes the global leaderboard. Stats are per-player (grouped by PUUID) and independent of
 * which challenge(s) a player is in — someone can join several challenges, and a match they played
 * is only ever counted once even if more than one of those challenges synced it. Only runs from
 * {@link LeaderboardRefreshScheduler} 4x/day — one query per challenge participation is an
 * acceptable simplicity/performance tradeoff at the current scale; batch it if it grows.
 */
@Service
public class LeaderboardComputationService {

    static final int LEADERBOARD_SIZE = 10;
    static final int RECENT_MATCHES_SIZE = 10;
    /** Minimum games required for win-rate rankings only (avoids tiny-sample spikes). */
    static final int MIN_GAMES_FOR_WIN_RATE = 20;
    /** Lower floor for the rolling 7-day window — a full season's worth of games isn't realistic in 7 days. */
    static final int MIN_GAMES_FOR_WIN_RATE_ROLLING = 8;
    private static final Duration ROLLING_WINDOW = Duration.ofDays(7);

    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeParticipantMatchRepository matchRepository;
    private final RankSnapshotRepository rankSnapshotRepository;
    private final LeaderboardProperties properties;
    private final ChampionIconUrlService championIconUrlService;

    public LeaderboardComputationService(
            ChallengeParticipantRepository participantRepository,
            ChallengeParticipantMatchRepository matchRepository,
            RankSnapshotRepository rankSnapshotRepository,
            LeaderboardProperties properties,
            ChampionIconUrlService championIconUrlService
    ) {
        this.participantRepository = participantRepository;
        this.matchRepository = matchRepository;
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.properties = properties;
        this.championIconUrlService = championIconUrlService;
    }

    public LeaderboardSnapshot compute(Instant now) {
        List<ChallengeParticipant> participants = participantRepository.findAll();
        Instant seasonStart = properties.seasonStartAt();
        Instant rollingStart = laterOf(seasonStart, now.minus(ROLLING_WINDOW));

        Map<String, PlayerRank> ranks = new HashMap<>();
        for (ChallengeParticipant participant : participants) {
            recordLatestRank(ranks, participant);
        }

        Map<String, List<ChallengeParticipant>> membershipsByPuuid = participants.stream()
                .collect(Collectors.groupingBy(ChallengeParticipant::getRiotPuuid));

        List<ParticipantWindowStats> seasonStats = new ArrayList<>();
        List<ParticipantWindowStats> rollingStats = new ArrayList<>();

        for (Map.Entry<String, List<ChallengeParticipant>> group : membershipsByPuuid.entrySet()) {
            List<TaggedMatch> history = mergeHistoryAcrossChallenges(group.getValue(), seasonStart);
            if (history.isEmpty()) {
                continue;
            }

            UUID primaryParticipantId = mostCommonParticipantId(history);
            ChallengeParticipant primaryMembership = group.getValue().stream()
                    .filter(member -> member.getId().equals(primaryParticipantId))
                    .findFirst()
                    .orElseGet(() -> group.getValue().get(0));
            PlayerIdentity identity = identityOf(primaryMembership);
            UUID primaryChallengeId = primaryMembership.getChallengeId();
            PlayerRank rank = ranks.get(group.getKey());

            seasonStats.add(toStats(identity, primaryParticipantId, primaryChallengeId, history, rank));

            List<TaggedMatch> rollingHistory = history.stream()
                    .filter(match -> !match.gameStart().isBefore(rollingStart))
                    .toList();
            if (!rollingHistory.isEmpty()) {
                rollingStats.add(toStats(identity, primaryParticipantId, primaryChallengeId, rollingHistory, rank));
            }
        }

        return new LeaderboardSnapshot(
                buildWindow(seasonStats, ranks, MIN_GAMES_FOR_WIN_RATE),
                buildWindow(rollingStats, ranks, MIN_GAMES_FOR_WIN_RATE_ROLLING),
                now
        );
    }

    /**
     * Merges a player's match history across every challenge they've joined into one deduped,
     * chronological list — a match synced by more than one of their challenges is only kept once.
     */
    private List<TaggedMatch> mergeHistoryAcrossChallenges(List<ChallengeParticipant> memberships, Instant since) {
        Map<String, TaggedMatch> byMatchId = new LinkedHashMap<>();
        for (ChallengeParticipant member : memberships) {
            List<ParticipantMatchHistorySince> history = matchRepository.findHistorySince(member.getId(), since);
            for (ParticipantMatchHistorySince row : history) {
                byMatchId.putIfAbsent(row.getMatchId(), new TaggedMatch(
                        row.getMatchId(),
                        row.isWin(),
                        row.getChampionId(),
                        row.getChallengeId(),
                        member.getId(),
                        row.getGameStart()
                ));
            }
        }
        return byMatchId.values().stream()
                .sorted(Comparator.comparing(TaggedMatch::gameStart))
                .toList();
    }

    /** Which challenge participation contributed the most matches — used only as display metadata. */
    private static UUID mostCommonParticipantId(List<TaggedMatch> history) {
        return history.stream()
                .collect(Collectors.groupingBy(TaggedMatch::participantId, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
    }

    private LeaderboardWindow buildWindow(
            List<ParticipantWindowStats> stats,
            Map<String, PlayerRank> ranks,
            int winRateMinGames
    ) {
        Map<String, ParticipantWindowStats> statsByPuuid = new HashMap<>();
        for (ParticipantWindowStats entry : stats) {
            statsByPuuid.putIfAbsent(entry.identity().puuid(), entry);
        }

        return new LeaderboardWindow(
                buildRankList(ranks, statsByPuuid),
                buildStatList(
                        stats,
                        winRateMinGames,
                        Comparator.comparingDouble(ParticipantWindowStats::winRate).reversed(),
                        ranks
                ),
                buildStatList(
                        stats.stream().filter(s -> s.lpGained() != null).toList(),
                        1,
                        Comparator.comparingInt(ParticipantWindowStats::lpGained).reversed(),
                        ranks
                ),
                buildStatList(
                        stats,
                        1,
                        Comparator.comparingInt(ParticipantWindowStats::winStreak).reversed(),
                        ranks
                ),
                winRateMinGames
        );
    }

    private void recordLatestRank(Map<String, PlayerRank> ranks, ChallengeParticipant participant) {
        RankSnapshot snapshot = rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(participant.getId(), SnapshotType.REFRESH)
                .or(() -> rankSnapshotRepository
                        .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(participant.getId(), SnapshotType.BASELINE))
                .orElse(null);
        if (snapshot == null) {
            return;
        }

        PlayerRank candidate = new PlayerRank(
                identityOf(participant),
                snapshot.getTier(),
                snapshot.getRankDivision(),
                snapshot.getLeaguePoints(),
                snapshot.getCapturedAt()
        );
        ranks.merge(
                participant.getRiotPuuid(),
                candidate,
                (existing, incoming) -> incoming.capturedAt().isAfter(existing.capturedAt()) ? incoming : existing
        );
    }

    private ParticipantWindowStats toStats(
            PlayerIdentity identity,
            UUID primaryParticipantId,
            UUID primaryChallengeId,
            List<TaggedMatch> historyAsc,
            PlayerRank rank
    ) {
        int gamesPlayed = historyAsc.size();
        List<Boolean> winsOrdered = historyAsc.stream().map(TaggedMatch::win).toList();
        int wins = (int) winsOrdered.stream().filter(Boolean::booleanValue).count();
        int losses = gamesPlayed - wins;
        double winRate = (double) wins / gamesPlayed;
        int winStreak = WinStreakCalculator.longestWinStreak(winsOrdered);

        Integer lpGained = null;
        if (gamesPlayed >= RankReplayService.MIN_MATCHES_FOR_RANK_ESTIMATE) {
            lpGained = RankReplayService.estimateFromMatches(winsOrdered)
                    .map(estimate -> RankScoreConverter.lpGained(
                            estimate.baseline().tier(), estimate.baseline().rankDivision(), estimate.baseline().leaguePoints(),
                            estimate.refresh().tier(), estimate.refresh().rankDivision(), estimate.refresh().leaguePoints()
                    ))
                    .orElse(null);
        }

        String tierForLp = rank != null && rank.tier() != null ? rank.tier() : "GOLD";

        return new ParticipantWindowStats(
                identity,
                primaryParticipantId,
                primaryChallengeId,
                wins,
                losses,
                gamesPlayed,
                winRate,
                winStreak,
                lpGained,
                recentMatches(historyAsc, tierForLp)
        );
    }

    private List<LeaderboardMatchHistoryResponse> recentMatches(List<TaggedMatch> historyAsc, String tier) {
        if (historyAsc.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, historyAsc.size() - RECENT_MATCHES_SIZE);
        List<TaggedMatch> slice = historyAsc.subList(from, historyAsc.size());
        List<LeaderboardMatchHistoryResponse> newestFirst = new ArrayList<>(slice.size());
        for (int i = slice.size() - 1; i >= 0; i--) {
            TaggedMatch row = slice.get(i);
            int lpDelta = row.win()
                    ? MatchLpEstimator.averageWinLp(tier)
                    : -MatchLpEstimator.averageLossLp(tier);
            newestFirst.add(new LeaderboardMatchHistoryResponse(
                    row.matchId(),
                    row.championId(),
                    championIconUrlService.buildApiPath(row.championId()),
                    row.win(),
                    lpDelta,
                    row.gameStart(),
                    row.challengeId(),
                    row.participantId()
            ));
        }
        return List.copyOf(newestFirst);
    }

    private List<LeaderboardEntryResponse> buildRankList(
            Map<String, PlayerRank> ranks,
            Map<String, ParticipantWindowStats> statsByPuuid
    ) {
        List<PlayerRank> sorted = ranks.values().stream()
                .sorted(Comparator.comparingInt(
                        (PlayerRank r) -> RankScoreConverter.toScore(r.tier(), r.rankDivision(), r.leaguePoints())
                ).reversed())
                .limit(LEADERBOARD_SIZE)
                .toList();

        List<LeaderboardEntryResponse> result = new ArrayList<>(sorted.size());
        int position = 1;
        for (PlayerRank rank : sorted) {
            ParticipantWindowStats stats = statsByPuuid.get(rank.identity().puuid());
            result.add(toRankEntry(rank, position++, stats));
        }
        return result;
    }

    private List<LeaderboardEntryResponse> buildStatList(
            List<ParticipantWindowStats> stats,
            int minGames,
            Comparator<ParticipantWindowStats> bestFirst,
            Map<String, PlayerRank> ranks
    ) {
        Map<String, ParticipantWindowStats> bestPerPlayer = new HashMap<>();
        for (ParticipantWindowStats candidate : stats) {
            if (candidate.gamesPlayed() < minGames) {
                continue;
            }
            bestPerPlayer.merge(
                    candidate.identity().puuid(),
                    candidate,
                    (a, b) -> bestFirst.compare(a, b) <= 0 ? a : b
            );
        }

        List<ParticipantWindowStats> sorted = bestPerPlayer.values().stream()
                .sorted(bestFirst)
                .limit(LEADERBOARD_SIZE)
                .toList();

        List<LeaderboardEntryResponse> result = new ArrayList<>(sorted.size());
        int position = 1;
        for (ParticipantWindowStats entry : sorted) {
            result.add(toStatEntry(entry, ranks, position++));
        }
        return result;
    }

    private LeaderboardEntryResponse toStatEntry(ParticipantWindowStats stats, Map<String, PlayerRank> ranks, int position) {
        PlayerRank rank = ranks.get(stats.identity().puuid());
        return new LeaderboardEntryResponse(
                stats.identity().puuid(),
                stats.identity().gameName(),
                stats.identity().tagLine(),
                stats.identity().gameName() + "#" + stats.identity().tagLine(),
                stats.identity().profileIconId(),
                rank != null ? rank.tier() : null,
                rank != null ? rank.rankDivision() : null,
                rank != null ? rank.leaguePoints() : 0,
                stats.wins(),
                stats.losses(),
                stats.gamesPlayed(),
                stats.winRate(),
                stats.winStreak(),
                stats.lpGained() != null ? stats.lpGained() : 0,
                position,
                stats.challengeId(),
                stats.participantId(),
                stats.recentMatches()
        );
    }

    private LeaderboardEntryResponse toRankEntry(PlayerRank rank, int position, ParticipantWindowStats stats) {
        return new LeaderboardEntryResponse(
                rank.identity().puuid(),
                rank.identity().gameName(),
                rank.identity().tagLine(),
                rank.identity().gameName() + "#" + rank.identity().tagLine(),
                rank.identity().profileIconId(),
                rank.tier(),
                rank.rankDivision(),
                rank.leaguePoints(),
                0, 0, 0, 0.0, 0, 0,
                position,
                stats != null ? stats.challengeId() : null,
                stats != null ? stats.participantId() : null,
                stats != null ? stats.recentMatches() : List.of()
        );
    }

    private static PlayerIdentity identityOf(ChallengeParticipant participant) {
        return new PlayerIdentity(
                participant.getRiotPuuid(),
                participant.getRiotGameName(),
                participant.getRiotTagLine(),
                participant.getProfileIconId()
        );
    }

    private static Instant laterOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private record PlayerIdentity(String puuid, String gameName, String tagLine, Integer profileIconId) {
    }

    private record PlayerRank(PlayerIdentity identity, String tier, String rankDivision, int leaguePoints, Instant capturedAt) {
    }

    /** A match, tagged with which of the player's challenge participations it was synced through. */
    private record TaggedMatch(
            String matchId,
            boolean win,
            Integer championId,
            UUID challengeId,
            UUID participantId,
            Instant gameStart
    ) {
    }

    private record ParticipantWindowStats(
            PlayerIdentity identity,
            UUID participantId,
            UUID challengeId,
            int wins,
            int losses,
            int gamesPlayed,
            double winRate,
            int winStreak,
            Integer lpGained,
            List<LeaderboardMatchHistoryResponse> recentMatches
    ) {
    }
}
