package com.riftchallenge.riot;

import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import java.util.List;

public final class RankReplayService {

    public record RankState(String tier, String rankDivision, int leaguePoints) {
    }

    private RankReplayService() {
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

    public static RankState applyBackward(RankState state, boolean win) {
        int score = RankScoreConverter.toScore(state.tier(), state.rankDivision(), state.leaguePoints());
        int delta = win
                ? -MatchLpEstimator.averageWinLp(state.tier())
                : MatchLpEstimator.averageLossLp(state.tier());
        RankScoreConverter.RankComponents components = RankScoreConverter.fromScore(score + delta);
        return new RankState(components.tier(), components.rankDivision(), components.leaguePoints());
    }
}
