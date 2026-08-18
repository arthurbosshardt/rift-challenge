package com.riftchallenge.riot;

import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RankReplayService {

    public static final int MIN_MATCHES_FOR_RANK_ESTIMATE = 3;

    public record RankState(String tier, String rankDivision, int leaguePoints) {
    }

    public record MatchBasedRankEstimate(RankState baseline, RankState refresh) {
    }

    private static final List<String> DIVISIONS = List.of("IV", "III", "II", "I");

    private static final List<RankState> END_ANCHOR_CANDIDATES = buildEndAnchorCandidates();

    private static final Map<String, Integer> TIER_ORDER = Map.ofEntries(
            Map.entry("IRON", 0),
            Map.entry("BRONZE", 1),
            Map.entry("SILVER", 2),
            Map.entry("GOLD", 3),
            Map.entry("PLATINUM", 4),
            Map.entry("EMERALD", 5),
            Map.entry("DIAMOND", 6),
            Map.entry("MASTER", 7),
            Map.entry("GRANDMASTER", 8),
            Map.entry("CHALLENGER", 9)
    );

    private RankReplayService() {
    }

    /**
     * Estimates challenge baseline and end ranks from match outcomes alone.
     * The end rank is chosen first (fin de période), then baseline is derived by replaying backward.
     */
    public static Optional<MatchBasedRankEstimate> estimateFromMatches(List<Boolean> winsOldestFirst) {
        if (winsOldestFirst.isEmpty()) {
            return Optional.empty();
        }

        RankState refresh = findBestEndAnchor(winsOldestFirst);
        RankState baseline = replayBackward(refresh, winsOldestFirst.reversed());
        return Optional.of(new MatchBasedRankEstimate(baseline, refresh));
    }

    /**
     * @deprecated Prefer {@link #estimateFromMatches(List)} which anchors on the end rank.
     */
    @Deprecated
    public static RankState findBestBaselineAnchor(List<Boolean> winsOldestFirst) {
        return estimateFromMatches(winsOldestFirst)
                .map(MatchBasedRankEstimate::baseline)
                .orElse(END_ANCHOR_CANDIDATES.getFirst());
    }

    public static RankState findBestEndAnchor(List<Boolean> winsOldestFirst) {
        if (winsOldestFirst.isEmpty()) {
            return END_ANCHOR_CANDIDATES.getFirst();
        }

        int wins = (int) winsOldestFirst.stream().filter(Boolean::booleanValue).count();
        int losses = winsOldestFirst.size() - wins;
        int netWins = wins - losses;
        double winRate = (double) wins / winsOldestFirst.size();
        List<Boolean> winsNewestFirst = winsOldestFirst.reversed();

        String maxTier = maxTierForMatchCount(winsOldestFirst.size());
        int maxTierOrder = tierOrder(maxTier);

        return END_ANCHOR_CANDIDATES.stream()
                .filter(candidate -> tierOrder(candidate.tier()) <= maxTierOrder)
                .min(Comparator.comparingInt(end -> scoreEndAnchor(
                        end,
                        winsOldestFirst,
                        winsNewestFirst,
                        wins,
                        losses,
                        netWins,
                        winRate
                )))
                .orElse(END_ANCHOR_CANDIDATES.getFirst());
    }

    static String maxTierForMatchCount(int matchCount) {
        if (matchCount <= 10) {
            return "GOLD";
        }
        if (matchCount <= 25) {
            return "PLATINUM";
        }
        if (matchCount <= 40) {
            return "EMERALD";
        }
        return "CHALLENGER";
    }

    public static RankState fromLeague(RiotLeagueEntryDto entry) {
        return new RankState(entry.tier(), entry.rank(), entry.leaguePoints());
    }

    public static RankState replayBackward(RankState state, List<Boolean> winsNewestFirst) {
        RankState current = state;
        for (boolean win : winsNewestFirst) {
            current = applyBackward(current, win);
        }
        return current;
    }

    public static RankState replayForward(RankState state, List<Boolean> winsOldestFirst) {
        RankState current = state;
        for (boolean win : winsOldestFirst) {
            current = applyForward(current, win);
        }
        return current;
    }

    public static RankState applyForward(RankState state, boolean win) {
        int score = RankScoreConverter.toScore(state.tier(), state.rankDivision(), state.leaguePoints());
        int delta = win
                ? MatchLpEstimator.averageWinLp(state.tier())
                : -MatchLpEstimator.averageLossLp(state.tier());
        RankScoreConverter.RankComponents components = RankScoreConverter.fromScore(score + delta);
        return new RankState(components.tier(), components.rankDivision(), components.leaguePoints());
    }

    public static RankState applyBackward(RankState state, boolean win) {
        int score = RankScoreConverter.toScore(state.tier(), state.rankDivision(), state.leaguePoints());
        int delta = win
                ? -MatchLpEstimator.averageWinLp(state.tier())
                : MatchLpEstimator.averageLossLp(state.tier());
        RankScoreConverter.RankComponents components = RankScoreConverter.fromScore(score + delta);
        return new RankState(components.tier(), components.rankDivision(), components.leaguePoints());
    }

    private static List<RankState> buildEndAnchorCandidates() {
        List<RankState> candidates = new ArrayList<>();
        for (String tier : List.of("IRON", "BRONZE", "SILVER", "GOLD", "PLATINUM", "EMERALD", "DIAMOND")) {
            for (String division : DIVISIONS) {
                candidates.add(new RankState(tier, division, 0));
                candidates.add(new RankState(tier, division, 50));
            }
        }
        candidates.add(new RankState("MASTER", null, 0));
        candidates.add(new RankState("GRANDMASTER", null, 0));
        candidates.add(new RankState("CHALLENGER", null, 0));
        return List.copyOf(candidates);
    }

    private static int scoreEndAnchor(
            RankState end,
            List<Boolean> winsOldestFirst,
            List<Boolean> winsNewestFirst,
            int wins,
            int losses,
            int netWins,
            double winRate
    ) {
        RankState start = replayBackward(end, winsNewestFirst);
        int lpDelta = RankScoreConverter.toScore(end.tier(), end.rankDivision(), end.leaguePoints())
                - RankScoreConverter.toScore(start.tier(), start.rankDivision(), start.leaguePoints());

        int score = 0;
        int endTierOrder = tierOrder(end.tier());
        int tierDistance = Math.abs(endTierOrder - tierOrder(start.tier()));

        if (netWins > 2 && lpDelta <= 0) {
            score += 5_000;
        }
        if (netWins < -2 && lpDelta >= 0) {
            score += 5_000;
        }

        if (Math.abs(netWins) <= 3) {
            score += tierDistance * 500;
            if (tierDistance == 0) {
                score -= endTierOrder * 25;
            }
        } else if (netWins > 3) {
            if (lpDelta <= 0) {
                score += 3_000;
            }
            score -= Math.min(endTierOrder, tierOrder("DIAMOND")) * (netWins / 4);
        } else {
            score += tierDistance * 200;
        }

        if (winRate < 0.52 && endTierOrder >= tierOrder("DIAMOND")) {
            score += 2_000;
        }
        if (winRate < 0.56 && endTierOrder >= tierOrder("DIAMOND")) {
            score += 700;
        }
        if (winRate < 0.55 && endTierOrder >= tierOrder("MASTER")) {
            score += 3_000;
        }
        if (winRate >= 0.55 && wins + losses >= 40 && endTierOrder < tierOrder("EMERALD")) {
            score += 600;
        }
        if (winRate >= 0.60 && wins + losses >= 50 && endTierOrder < tierOrder("DIAMOND")) {
            score += 400;
        }

        return score;
    }

    private static int tierOrder(String tier) {
        return TIER_ORDER.getOrDefault(tier.toUpperCase(Locale.ROOT), Integer.MAX_VALUE);
    }
}
