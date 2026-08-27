package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.DuoMatchHistoryResponse;
import com.riftchallenge.challenge.dto.ParticipantMatchHistoryResponse;
import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.riot.ChampionIconUrlService;
import com.riftchallenge.riot.MatchLpEstimator;
import com.riftchallenge.riot.RankReplayService;
import com.riftchallenge.riot.RankReplayService.RankState;
import com.riftchallenge.leaderboard.AccountMatchRepository;
import com.riftchallenge.leaderboard.AccountMatchRepository.ParticipantMatchHistoryRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchHistoryService {

    private final AccountMatchRepository participantMatchRepository;
    private final ChampionIconUrlService championIconUrlService;

    public MatchHistoryService(
            AccountMatchRepository participantMatchRepository,
            ChampionIconUrlService championIconUrlService
    ) {
        this.participantMatchRepository = participantMatchRepository;
        this.championIconUrlService = championIconUrlService;
    }

    @Transactional(readOnly = true)
    public List<ParticipantMatchHistoryResponse> buildForParticipant(
            ChallengeParticipant participant,
            ParticipantProgressResponse progress,
            Challenge challenge
    ) {
        if (challenge == null) {
            return List.of();
        }

        List<ParticipantMatchHistoryRow> rows = MaxGamesCap.capToOldest(
                participantMatchRepository.findHistoryByParticipantIdAndChallengeId(
                        participant.getId(),
                        participant.getChallengeId()
                ),
                challenge.getMaxGames(),
                ParticipantMatchHistoryRow::getGameStart
        );
        if (rows.isEmpty()) {
            return List.of();
        }

        RankState state = rankStateFromProgress(progress);
        List<ParticipantMatchHistoryResponse> history = new ArrayList<>();

        for (ParticipantMatchHistoryRow row : rows) {
            if (!isWithinChallengeWindow(row.getGameStart(), challenge.getStartAt(), challenge.getEndAt())) {
                continue;
            }

            int lpDelta = row.isWin()
                    ? MatchLpEstimator.averageWinLp(state.tier())
                    : -MatchLpEstimator.averageLossLp(state.tier());

            history.add(new ParticipantMatchHistoryResponse(
                    row.getMatchId(),
                    row.getChampionId(),
                    championIconUrlService.buildApiPath(row.getChampionId()),
                    row.isWin(),
                    lpDelta,
                    row.getGameStart()
            ));

            state = RankReplayService.applyBackward(state, row.isWin());
        }

        return List.copyOf(history);
    }

    @Transactional(readOnly = true)
    public List<DuoMatchHistoryResponse> buildForDuo(
            Challenge challenge,
            ChallengeParticipant player1,
            ChallengeParticipant player2,
            ParticipantProgressResponse progress1,
            ParticipantProgressResponse progress2,
            Set<String> togetherMatchIds
    ) {
        if (challenge == null || togetherMatchIds.isEmpty()) {
            return List.of();
        }

        UUID challengeId = challenge.getId();
        Set<String> inWindowTogetherMatchIds = filterTogetherMatchesInChallengeWindow(
                challengeId,
                togetherMatchIds
        );
        if (inWindowTogetherMatchIds.isEmpty()) {
            return List.of();
        }

        List<AccountMatchRepository.DuoMatchHistoryRow> rows = MaxGamesCap.capToOldest(
                participantMatchRepository.findDuoHistoryByParticipantIdsAndChallengeId(
                        player1.getId(),
                        player2.getId(),
                        challengeId,
                        inWindowTogetherMatchIds
                ),
                challenge.getMaxGames(),
                AccountMatchRepository.DuoMatchHistoryRow::getGameStart
        );
        if (rows.isEmpty()) {
            return List.of();
        }

        RankState state1 = rankStateFromProgress(progress1);
        RankState state2 = rankStateFromProgress(progress2);
        List<DuoMatchHistoryResponse> history = new ArrayList<>();

        for (AccountMatchRepository.DuoMatchHistoryRow row : rows) {
            if (!isWithinChallengeWindow(row.getGameStart(), challenge.getStartAt(), challenge.getEndAt())) {
                continue;
            }

            int lpDelta1 = row.isWin()
                    ? MatchLpEstimator.averageWinLp(state1.tier())
                    : -MatchLpEstimator.averageLossLp(state1.tier());
            int lpDelta2 = row.isWin()
                    ? MatchLpEstimator.averageWinLp(state2.tier())
                    : -MatchLpEstimator.averageLossLp(state2.tier());

            history.add(new DuoMatchHistoryResponse(
                    row.getMatchId(),
                    row.isWin(),
                    nullSafeChampionId(row.getPlayer1ChampionId()),
                    championIconUrlService.buildApiPath(row.getPlayer1ChampionId()),
                    lpDelta1,
                    nullSafeChampionId(row.getPlayer2ChampionId()),
                    championIconUrlService.buildApiPath(row.getPlayer2ChampionId()),
                    lpDelta2,
                    row.getGameStart()
            ));

            state1 = RankReplayService.applyBackward(state1, row.isWin());
            state2 = RankReplayService.applyBackward(state2, row.isWin());
        }

        return List.copyOf(history);
    }

    static boolean isWithinChallengeWindow(Instant gameStart, Instant startAt, Instant endAt) {
        if (gameStart == null || startAt == null) {
            return false;
        }
        if (gameStart.isBefore(startAt)) {
            return false;
        }
        return endAt == null || gameStart.isBefore(endAt);
    }

    private Set<String> filterTogetherMatchesInChallengeWindow(UUID challengeId, Set<String> togetherMatchIds) {
        if (togetherMatchIds.isEmpty()) {
            return Set.of();
        }

        List<String> inWindowMatchIds = participantMatchRepository.findMatchIdsInChallengeWindowForMatches(
                challengeId,
                togetherMatchIds
        );
        return new HashSet<>(inWindowMatchIds);
    }

    private static RankState rankStateFromProgress(ParticipantProgressResponse progress) {
        if (!progress.hasRankData() || progress.currentTier() == null) {
            return new RankState("GOLD", "IV", 0);
        }

        return new RankState(
                progress.currentTier(),
                progress.currentRank() == null ? "IV" : progress.currentRank(),
                progress.currentLp()
        );
    }

    private static int nullSafeChampionId(Integer championId) {
        return championId == null ? 0 : championId;
    }
}
