package com.riftrace.race;

import com.riftrace.race.dto.ParticipantProgressResponse;
import com.riftrace.synchronization.RaceParticipantMatchRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class DuoEligibilityService {

    private final RaceParticipantMatchRepository participantMatchRepository;

    public DuoEligibilityService(RaceParticipantMatchRepository participantMatchRepository) {
        this.participantMatchRepository = participantMatchRepository;
    }

    public DuoEligibility evaluate(RaceParticipant player1, RaceParticipant player2) {
        Set<String> player1Matches = new HashSet<>(
                participantMatchRepository.findMatchIdsByParticipantId(player1.getId())
        );
        Set<String> player2Matches = new HashSet<>(
                participantMatchRepository.findMatchIdsByParticipantId(player2.getId())
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
                    formatRiotId(player1) + " a joué en SoloQ sans son coéquipier",
                    togetherMatches
            );
        }

        return new DuoEligibility(
                false,
                formatRiotId(player2) + " a joué en SoloQ sans son coéquipier",
                togetherMatches
        );
    }

    public DuoMatchStats statsForTogetherMatches(RaceParticipant referencePlayer, Set<String> togetherMatches) {
        if (togetherMatches.isEmpty()) {
            return new DuoMatchStats(0, 0);
        }

        List<String> matchIds = List.copyOf(togetherMatches);
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

    private static String formatRiotId(RaceParticipant participant) {
        return participant.getRiotGameName() + "#" + participant.getRiotTagLine();
    }

    public record DuoEligibility(boolean eligible, String reason, Set<String> togetherMatchIds) {
    }

    public record DuoMatchStats(int wins, int losses) {
    }
}
