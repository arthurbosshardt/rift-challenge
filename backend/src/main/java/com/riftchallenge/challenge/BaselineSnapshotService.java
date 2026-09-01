package com.riftchallenge.challenge;

import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import com.riftchallenge.synchronization.RankSnapshot;
import com.riftchallenge.synchronization.RankSnapshotRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Captures a participant's starting rank when they join a challenge that hasn't started yet —
 * shared by the SOLOQ ({@link ChallengeParticipantService}) and DUOQ ({@link ChallengeDuoService})
 * add-participant flows, which otherwise duplicated this exact logic.
 */
@Service
public class BaselineSnapshotService {

    private final RiotLeagueClient riotLeagueClient;
    private final RankSnapshotRepository rankSnapshotRepository;
    private final Clock clock;

    public BaselineSnapshotService(
            RiotLeagueClient riotLeagueClient,
            RankSnapshotRepository rankSnapshotRepository,
            Clock clock
    ) {
        this.riotLeagueClient = riotLeagueClient;
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.clock = clock;
    }

    public void captureBaselineIfRanked(ChallengeParticipant participant, ChallengeRegion region) {
        try {
            riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid(), region)
                    .ifPresent(entry -> rankSnapshotRepository.save(toBaselineSnapshot(participant.getId(), entry)));
        } catch (ResponseStatusException ignored) {
            // Baseline can be captured on the first refresh if Riot is temporarily unavailable.
        }
    }

    private RankSnapshot toBaselineSnapshot(UUID participantId, RiotLeagueEntryDto entry) {
        return RankSnapshot.create(
                participantId,
                clock.instant(),
                RankSnapshot.SnapshotType.BASELINE,
                entry.queueType(),
                entry.tier(),
                entry.rank(),
                entry.leaguePoints(),
                entry.wins(),
                entry.losses()
        );
    }
}
