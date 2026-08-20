package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.DuoProgressResponse;
import com.riftchallenge.challenge.dto.ParticipantProgressResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChallengeDuoProgressService {

    private final ChallengeDuoRepository challengeDuoRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeProgressService progressService;
    private final DuoEligibilityService duoEligibilityService;
    private final MatchHistoryService matchHistoryService;

    public ChallengeDuoProgressService(
            ChallengeDuoRepository challengeDuoRepository,
            ChallengeParticipantRepository participantRepository,
            ChallengeProgressService progressService,
            DuoEligibilityService duoEligibilityService,
            MatchHistoryService matchHistoryService
    ) {
        this.challengeDuoRepository = challengeDuoRepository;
        this.participantRepository = participantRepository;
        this.progressService = progressService;
        this.duoEligibilityService = duoEligibilityService;
        this.matchHistoryService = matchHistoryService;
    }

    @Transactional(readOnly = true)
    public List<DuoProgressResponse> buildProgress(Challenge challenge) {
        return buildProgress(challenge, true);
    }

    @Transactional(readOnly = true)
    public List<DuoProgressResponse> buildPreview(Challenge challenge) {
        return buildProgress(challenge, false);
    }

    private List<DuoProgressResponse> buildProgress(Challenge challenge, boolean includeMatchHistory) {
        List<ChallengeDuo> duos = challengeDuoRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId());

        List<DuoProgressResponse> unsorted = new ArrayList<>();
        for (ChallengeDuo duo : duos) {
            unsorted.add(buildForDuo(duo, includeMatchHistory, challenge));
        }

        unsorted.sort(Comparator.comparingInt(DuoProgressResponse::combinedRankScore).reversed());

        List<DuoProgressResponse> ranked = new ArrayList<>();
        for (int index = 0; index < unsorted.size(); index++) {
            ranked.add(unsorted.get(index).withPosition(index + 1));
        }
        return ranked;
    }

    private DuoProgressResponse buildForDuo(ChallengeDuo duo, boolean includeMatchHistory, Challenge challenge) {
        List<ChallengeParticipant> members = participantRepository.findByDuoIdOrderByCreatedAtAsc(duo.getId());
        if (members.size() != 2) {
            throw new IllegalStateException("Duo must contain exactly two participants");
        }

        ChallengeParticipant player1 = members.get(0);
        ChallengeParticipant player2 = members.get(1);

        ParticipantProgressResponse progress1 = progressService.buildForParticipant(player1, includeMatchHistory, challenge);
        ParticipantProgressResponse progress2 = progressService.buildForParticipant(player2, includeMatchHistory, challenge);

        DuoEligibilityService.DuoEligibility eligibility = duoEligibilityService.evaluate(
                duo.getChallengeId(),
                player1,
                player2
        );
        DuoEligibilityService.DuoMatchStats stats = duoEligibilityService.statsForTogetherMatches(
                player1,
                eligibility.togetherMatchIds(),
                challenge.getMaxGames()
        );
        if (stats.wins() + stats.losses() == 0) {
            stats = resolveDuoMatchStatsFallback(eligibility, progress1, progress2);
        }

        int combinedRankScore = progress1.rankScore() + progress2.rankScore();
        int combinedLpGained = progress1.lpGained() + progress2.lpGained();

        return new DuoProgressResponse(
                duo.getId(),
                progress1,
                progress2,
                combinedRankScore,
                combinedLpGained,
                stats.wins(),
                stats.losses(),
                winRate(stats.wins(), stats.losses()),
                eligibility.eligible(),
                eligibility.reason(),
                0,
                includeMatchHistory
                        ? matchHistoryService.buildForDuo(
                                challenge,
                                player1,
                                player2,
                                progress1,
                                progress2,
                                eligibility.togetherMatchIds()
                        )
                        : List.of()
        );
    }

    public Map<UUID, List<ChallengeParticipant>> groupParticipantsByDuo(List<ChallengeParticipant> participants) {
        return participants.stream()
                .filter(participant -> participant.getDuoId() != null)
                .collect(Collectors.groupingBy(ChallengeParticipant::getDuoId));
    }

    private static DuoEligibilityService.DuoMatchStats resolveDuoMatchStatsFallback(
            DuoEligibilityService.DuoEligibility eligibility,
            ParticipantProgressResponse progress1,
            ParticipantProgressResponse progress2
    ) {
        if (!eligibility.eligible()) {
            return new DuoEligibilityService.DuoMatchStats(0, 0);
        }

        if (progress1.wins() + progress1.losses() > 0) {
            return new DuoEligibilityService.DuoMatchStats(progress1.wins(), progress1.losses());
        }
        if (progress2.wins() + progress2.losses() > 0) {
            return new DuoEligibilityService.DuoMatchStats(progress2.wins(), progress2.losses());
        }
        return new DuoEligibilityService.DuoMatchStats(0, 0);
    }

    private static double winRate(int wins, int losses) {
        int total = wins + losses;
        if (total == 0) {
            return 0.0;
        }
        return (double) wins / total;
    }
}
