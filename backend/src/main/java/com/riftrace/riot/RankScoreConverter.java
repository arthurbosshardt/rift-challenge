package com.riftrace.riot;

import java.util.Locale;
import java.util.Map;

public final class RankScoreConverter {

    private static final Map<String, Integer> TIER_BASE = Map.ofEntries(
            Map.entry("IRON", 0),
            Map.entry("BRONZE", 400),
            Map.entry("SILVER", 800),
            Map.entry("GOLD", 1200),
            Map.entry("PLATINUM", 1600),
            Map.entry("EMERALD", 2000),
            Map.entry("DIAMOND", 2400),
            Map.entry("MASTER", 2800),
            Map.entry("GRANDMASTER", 3100),
            Map.entry("CHALLENGER", 3400)
    );

    private static final Map<String, Integer> DIVISION_VALUE = Map.of(
            "IV", 0,
            "III", 100,
            "II", 200,
            "I", 300
    );

    private RankScoreConverter() {
    }

    public static int toScore(String tier, String rankDivision, int leaguePoints) {
        String normalizedTier = tier.toUpperCase(Locale.ROOT);
        int tierBase = TIER_BASE.getOrDefault(normalizedTier, 0);

        if (normalizedTier.equals("MASTER")
                || normalizedTier.equals("GRANDMASTER")
                || normalizedTier.equals("CHALLENGER")) {
            return tierBase + leaguePoints;
        }

        int divisionBase = 0;
        if (rankDivision != null && !rankDivision.isBlank()) {
            divisionBase = DIVISION_VALUE.getOrDefault(rankDivision.toUpperCase(Locale.ROOT), 0);
        }

        return tierBase + divisionBase + leaguePoints;
    }

    public static int lpGained(String baselineTier, String baselineRank, int baselineLp,
                               String currentTier, String currentRank, int currentLp) {
        int baselineScore = toScore(baselineTier, baselineRank, baselineLp);
        int currentScore = toScore(currentTier, currentRank, currentLp);
        return currentScore - baselineScore;
    }
}
