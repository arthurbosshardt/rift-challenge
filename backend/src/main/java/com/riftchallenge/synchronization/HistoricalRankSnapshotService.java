package com.riftchallenge.synchronization;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.riot.RankReplayService.RankState;
import com.riftchallenge.riot.RiotLeagueClient;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoricalRankSnapshotService {

    private final RankSnapshotRepository rankSnapshotRepository;

    public HistoricalRankSnapshotService(RankSnapshotRepository rankSnapshotRepository) {
        this.rankSnapshotRepository = rankSnapshotRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveHistoricalSnapshots(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            RankState baselineState,
            RankState refreshState,
            int challengeWindowWins,
            int challengeWindowLosses,
            boolean estimated
    ) {
        Instant refreshAt = challenge.getEndAt() != null && now.isAfter(challenge.getEndAt())
                ? challenge.getEndAt()
                : now;

        rankSnapshotRepository.deleteByParticipantIdAndSnapshotType(
                participant.getId(),
                RankSnapshot.SnapshotType.BASELINE
        );
        rankSnapshotRepository.deleteByParticipantIdAndSnapshotType(
                participant.getId(),
                RankSnapshot.SnapshotType.REFRESH
        );

        rankSnapshotRepository.save(toSnapshot(
                participant.getId(),
                challenge.getStartAt(),
                RankSnapshot.SnapshotType.BASELINE,
                baselineState,
                0,
                0,
                estimated
        ));

        rankSnapshotRepository.save(toSnapshot(
                participant.getId(),
                refreshAt,
                RankSnapshot.SnapshotType.REFRESH,
                refreshState,
                challengeWindowWins,
                challengeWindowLosses,
                estimated
        ));
    }

    private static RankSnapshot toSnapshot(
            UUID participantId,
            Instant capturedAt,
            RankSnapshot.SnapshotType snapshotType,
            RankState state,
            int wins,
            int losses,
            boolean estimated
    ) {
        return RankSnapshot.create(
                participantId,
                capturedAt,
                snapshotType,
                RiotLeagueClient.RANKED_SOLO_QUEUE,
                state.tier(),
                state.rankDivision(),
                state.leaguePoints(),
                wins,
                losses,
                estimated
        );
    }
}
