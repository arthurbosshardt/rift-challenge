package com.riftchallenge.challenge;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * When a challenge caps its window by number of games instead of an end date, only the
 * earliest {@code maxGames} matches (per participant, or per duo for "together" matches)
 * count towards stats, LP and rank. Everything played after that cap is ignored.
 */
final class MaxGamesCap {

    private MaxGamesCap() {
    }

    /**
     * @param rowsDescendingByGameStart rows ordered most-recent-first (the natural order of the
     *                                  window queries)
     * @return the rows capped to the earliest {@code maxGames} entries, still ordered
     *         most-recent-first, or the input unchanged if {@code maxGames} is null or not exceeded
     */
    static <T> List<T> capToOldest(List<T> rowsDescendingByGameStart, Integer maxGames, Function<T, Instant> gameStartOf) {
        if (maxGames == null || rowsDescendingByGameStart.size() <= maxGames) {
            return rowsDescendingByGameStart;
        }

        return rowsDescendingByGameStart.stream()
                .sorted(Comparator.comparing(gameStartOf))
                .limit(maxGames)
                .sorted(Comparator.comparing(gameStartOf, Comparator.reverseOrder()))
                .toList();
    }
}
