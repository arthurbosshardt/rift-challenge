package com.riftchallenge.leaderboard;

import com.riftchallenge.account.UserRiotAccount;
import com.riftchallenge.account.UserRiotAccountRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountMatchRepository.AccountMatchHistoryRow;
import com.riftchallenge.leaderboard.dto.LeaderboardEntryResponse;
import com.riftchallenge.leaderboard.dto.LeaderboardMatchHistoryResponse;
import com.riftchallenge.leaderboard.dto.LeaderboardSnapshot;
import com.riftchallenge.leaderboard.dto.LeaderboardWindow;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.MatchLpEstimator;
import com.riftchallenge.riot.RankReplayService;
import com.riftchallenge.riot.RankScoreConverter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Computes the global leaderboard from data synced independently of challenges (see
 * {@link LeaderboardAccountSyncService}) — every linked Riot account counts, regardless of
 * whether or which challenge(s) its owner has joined. Only runs from
 * {@link LeaderboardCacheService#refresh}, right after the account sync.
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

    private final UserRiotAccountRepository userRiotAccountRepository;
    private final LeaderboardAccountMatchRepository matchRepository;
    private final LeaderboardAccountRankRepository rankRepository;
    private final LeaderboardProperties properties;
    private final ChampionIconUrlService championIconUrlService;

    public LeaderboardComputationService(
            UserRiotAccountRepository userRiotAccountRepository,
            LeaderboardAccountMatchRepository matchRepository,
            LeaderboardAccountRankRepository rankRepository,
            LeaderboardProperties properties,
            ChampionIconUrlService championIconUrlService
    ) {
        this.userRiotAccountRepository = userRiotAccountRepository;
        this.matchRepository = matchRepository;
        this.rankRepository = rankRepository;
        this.properties = properties;
        this.championIconUrlService = championIconUrlService;
    }

    public LeaderboardSnapshot compute(Instant now) {
        List<UserRiotAccount> accounts = userRiotAccountRepository.findAll();
        Instant seasonStart = properties.seasonStartAt();
        Instant rollingStart = laterOf(seasonStart, now.minus(ROLLING_WINDOW));

        Map<String, PlayerRank> ranks = new HashMap<>();
        for (UserRiotAccount account : accounts) {
            recordLatestRank(ranks, account);
        }

        List<ParticipantWindowStats> seasonStats = new ArrayList<>();
        List<ParticipantWindowStats> rollingStats = new ArrayList<>();

        for (UserRiotAccount account : accounts) {
            List<TaggedMatch> history = accountHistorySince(account.getRiotPuuid(), seasonStart);
            if (history.isEmpty()) {
                continue;
            }

            PlayerIdentity identity = identityOf(account);
            PlayerRank rank = ranks.get(account.getRiotPuuid());

            seasonStats.add(toStats(identity, history, rank));

            List<TaggedMatch> rollingHistory = history.stream()
                    .filter(match -> !match.gameStart().isBefore(rollingStart))
                    .toList();
            if (!rollingHistory.isEmpty()) {
                rollingStats.add(toStats(identity, rollingHistory, rank));
            }
        }

        return new LeaderboardSnapshot(
                buildWindow(seasonStats, ranks, MIN_GAMES_FOR_WIN_RATE),
                buildWindow(rollingStats, ranks, MIN_GAMES_FOR_WIN_RATE_ROLLING),
                now
        );
    }

    private List<TaggedMatch> accountHistorySince(String riotPuuid, Instant since) {
        return matchRepository.findHistorySince(riotPuuid, since).stream()
                .map(row -> new TaggedMatch(row.getMatchId(), row.isWin(), row.getChampionId(), row.getGameStart()))
                .sorted(Comparator.comparing(TaggedMatch::gameStart))
                .toList();
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

    private void recordLatestRank(Map<String, PlayerRank> ranks, UserRiotAccount account) {
        rankRepository.findByRiotPuuid(account.getRiotPuuid()).ifPresent(snapshot -> ranks.put(
                account.getRiotPuuid(),
                new PlayerRank(
                        identityOf(account),
                        snapshot.getTier(),
                        snapshot.getRankDivision(),
                        snapshot.getLeaguePoints(),
                        snapshot.getCapturedAt()
                )
        ));
    }

    private ParticipantWindowStats toStats(
            PlayerIdentity identity,
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
                    row.gameStart()
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
                stats != null ? stats.recentMatches() : List.of()
        );
    }

    private static PlayerIdentity identityOf(UserRiotAccount account) {
        return new PlayerIdentity(
                account.getRiotPuuid(),
                account.getRiotGameName(),
                account.getRiotTagLine(),
                account.getProfileIconId()
        );
    }

    private static Instant laterOf(Instant a, Instant b) {
        return a.isAfter(b) ? a : b;
    }

    private record PlayerIdentity(String puuid, String gameName, String tagLine, Integer profileIconId) {
    }

    private record PlayerRank(PlayerIdentity identity, String tier, String rankDivision, int leaguePoints, Instant capturedAt) {
    }

    private record TaggedMatch(String matchId, boolean win, Integer championId, Instant gameStart) {
    }

    private record ParticipantWindowStats(
            PlayerIdentity identity,
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
