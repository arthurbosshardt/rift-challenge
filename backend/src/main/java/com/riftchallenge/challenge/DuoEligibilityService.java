package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import com.riftchallenge.leaderboard.AccountMatchRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DuoEligibilityService {

    private final AccountMatchRepository participantMatchRepository;

    public DuoEligibilityService(AccountMatchRepository participantMatchRepository) {
        this.participantMatchRepository = participantMatchRepository;
    }

    public DuoEligibility evaluate(UUID challengeId, ChallengeParticipant player1, ChallengeParticipant player2) {
        Set<String> player1Matches = new HashSet<>(
                participantMatchRepository.findMatchIdsInChallengeWindow(player1.getId(), challengeId)
        );
        Set<String> player2Matches = new HashSet<>(
                participantMatchRepository.findMatchIdsInChallengeWindow(player2.getId(), challengeId)
        );

        Set<String> togetherMatches = new HashSet<>(player1Matches);
        togetherMatches.retainAll(player2Matches);

        if (player1Matches.equals(togetherMatches) && player2Matches.equals(togetherMatches)) {
            return new DuoEligibility(true, null, togetherMatches);
        }

        Set<String> soloPlayer1 = new HashSet<>(player1Matches);
        soloPlayer1.removeAll(player2Matches);
        if (!soloPlayer1.isEmpty()) {
            return new DuoEligibility(
                    false,
                    "SOLOQ_WITHOUT_PARTNER|" + formatRiotId(player1),
                    togetherMatches
            );
        }

        return new DuoEligibility(
                false,
                "SOLOQ_WITHOUT_PARTNER|" + formatRiotId(player2),
                togetherMatches
        );
    }

    public DuoMatchStats statsForTogetherMatches(
            ChallengeParticipant referencePlayer,
            Set<String> togetherMatches,
            Integer maxGames
    ) {
        if (togetherMatches.isEmpty()) {
            return new DuoMatchStats(0, 0);
        }

        List<String> matchIds = cappedMatchIds(togetherMatches, maxGames);
        int wins = (int) participantMatchRepository.countWinsByParticipantIdAndMatchIds(
                referencePlayer.getId(),
                matchIds
        );
        int losses = (int) participantMatchRepository.countLossesByParticipantIdAndMatchIds(
                referencePlayer.getId(),
                matchIds
        );
        return new DuoMatchStats(wins, losses);
    }

    private List<String> cappedMatchIds(Set<String> matchIds, Integer maxGames) {
        if (maxGames == null || matchIds.size() <= maxGames) {
            return List.copyOf(matchIds);
        }

        List<AccountMatchRepository.MatchIdAndGameStart> capped = MaxGamesCap.capToOldest(
                participantMatchRepository.findGameStartsForMatchIds(matchIds),
                maxGames,
                AccountMatchRepository.MatchIdAndGameStart::getGameStart
        );
        return capped.stream().map(AccountMatchRepository.MatchIdAndGameStart::getMatchId).toList();
    }

    private static String formatRiotId(ChallengeParticipant participant) {
        return participant.getRiotGameName() + "#" + participant.getRiotTagLine();
    }

    public record DuoEligibility(boolean eligible, String reason, Set<String> togetherMatchIds) {
    }

    public record DuoMatchStats(int wins, int losses) {
    }
}
