package com.riftchallenge.riot;

import java.util.Locale;

public final class MatchLpEstimator {

    private MatchLpEstimator() {
    }

    /**
     * Estimates net LP change from ranked solo wins/losses in the challenge when no valid
     * baseline snapshot exists (e.g. first refresh captured baseline and refresh together).
     */
    public static int estimateLpGainedFromMatches(int wins, int losses, String tier) {
        if (wins == 0 && losses == 0) {
            return 0;
        }

        int winLp = averageWinLp(tier);
        int lossLp = averageLossLp(tier);
        return wins * winLp - losses * lossLp;
    }

    public static int averageWinLp(String tier) {
        String normalized = tier == null ? "" : tier.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CHALLENGER", "GRANDMASTER", "MASTER" -> 12;
            case "DIAMOND", "EMERALD" -> 18;
            case "PLATINUM", "GOLD" -> 20;
            default -> 22;
        };
    }

    public static int averageLossLp(String tier) {
        String normalized = tier == null ? "" : tier.toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CHALLENGER", "GRANDMASTER", "MASTER" -> 12;
            case "DIAMOND", "EMERALD" -> 18;
            case "PLATINUM", "GOLD" -> 20;
            default -> 18;
        };
    }
}
