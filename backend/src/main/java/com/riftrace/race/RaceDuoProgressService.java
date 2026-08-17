package com.riftrace.race;

import com.riftrace.race.dto.DuoProgressResponse;
import com.riftrace.race.dto.ParticipantProgressResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RaceDuoProgressService {

    private final RaceDuoRepository raceDuoRepository;
    private final RaceParticipantRepository participantRepository;
    private final RaceProgressService progressService;
    private final DuoEligibilityService duoEligibilityService;

    public RaceDuoProgressService(
            RaceDuoRepository raceDuoRepository,
            RaceParticipantRepository participantRepository,
            RaceProgressService progressService,
            DuoEligibilityService duoEligibilityService
    ) {
        this.raceDuoRepository = raceDuoRepository;
        this.participantRepository = participantRepository;
        this.progressService = progressService;
        this.duoEligibilityService = duoEligibilityService;
    }

    @Transactional(readOnly = true)
    public List<DuoProgressResponse> buildProgress(UUID raceId) {
        List<RaceDuo> duos = raceDuoRepository.findByRaceIdOrderByCreatedAtAsc(raceId);

        List<DuoProgressResponse> unsorted = new ArrayList<>();
        for (RaceDuo duo : duos) {
            unsorted.add(buildForDuo(duo));
        }

        unsorted.sort(Comparator.comparingInt(DuoProgressResponse::combinedRankScore).reversed());

        List<DuoProgressResponse> ranked = new ArrayList<>();
        for (int index = 0; index < unsorted.size(); index++) {
            ranked.add(unsorted.get(index).withPosition(index + 1));
        }
        return ranked;
    }

    private DuoProgressResponse buildForDuo(RaceDuo duo) {
        List<RaceParticipant> members = participantRepository.findByDuoIdOrderByCreatedAtAsc(duo.getId());
        if (members.size() != 2) {
            throw new IllegalStateException("Duo must contain exactly two participants");
        }

        RaceParticipant player1 = members.get(0);
        RaceParticipant player2 = members.get(1);

        ParticipantProgressResponse progress1 = progressService.buildForParticipant(player1);
        ParticipantProgressResponse progress2 = progressService.buildForParticipant(player2);

        DuoEligibilityService.DuoEligibility eligibility = duoEligibilityService.evaluate(player1, player2);
        DuoEligibilityService.DuoMatchStats stats = duoEligibilityService.statsForTogetherMatches(
                player1,
                eligibility.togetherMatchIds()
        );

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
                0
        );
    }

    public Map<UUID, List<RaceParticipant>> groupParticipantsByDuo(List<RaceParticipant> participants) {
        return participants.stream()
                .filter(participant -> participant.getDuoId() != null)
                .collect(Collectors.groupingBy(RaceParticipant::getDuoId));
    }

    private static double winRate(int wins, int losses) {
        int total = wins + losses;
        if (total == 0) {
            return 0.0;
        }
        return (double) wins / total;
    }
}
