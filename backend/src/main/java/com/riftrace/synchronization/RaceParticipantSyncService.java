package com.riftrace.synchronization;

import com.riftrace.race.ParticipantProfileService;
import com.riftrace.race.Race;
import com.riftrace.race.RaceParticipant;
import com.riftrace.riot.RiotLeagueClient;
import com.riftrace.riot.RiotMatchClient;
import com.riftrace.riot.dto.RiotLeagueEntryDto;
import com.riftrace.riot.dto.RiotMatchDetailDto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RaceParticipantSyncService {

    static final int MAX_NEW_MATCHES_PER_REFRESH = 10;

    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotMatchRepository riotMatchRepository;
    private final RaceParticipantMatchRepository participantMatchRepository;
    private final RiotLeagueClient riotLeagueClient;
    private final RiotMatchClient riotMatchClient;
    private final ParticipantProfileService participantProfileService;

    public RaceParticipantSyncService(
            RankSnapshotRepository rankSnapshotRepository,
            RiotMatchRepository riotMatchRepository,
            RaceParticipantMatchRepository participantMatchRepository,
            RiotLeagueClient riotLeagueClient,
            RiotMatchClient riotMatchClient,
            ParticipantProfileService participantProfileService
    ) {
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotMatchRepository = riotMatchRepository;
        this.participantMatchRepository = participantMatchRepository;
        this.riotLeagueClient = riotLeagueClient;
        this.riotMatchClient = riotMatchClient;
        this.participantProfileService = participantProfileService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void syncParticipant(Race race, RaceParticipant participant, Instant now) {
        ensureBaselineSnapshot(participant, now);
        syncMatches(race, participant);
        captureRefreshSnapshot(participant, now);
        participantProfileService.ensureProfileIcon(participant.getId());
    }

    private void ensureBaselineSnapshot(RaceParticipant participant, Instant now) {
        boolean hasBaseline = rankSnapshotRepository
                .findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
                        participant.getId(),
                        RankSnapshot.SnapshotType.BASELINE
                )
                .isPresent();

        if (!hasBaseline) {
            riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid())
                    .ifPresent(entry -> rankSnapshotRepository.save(
                            toSnapshot(participant.getId(), now, RankSnapshot.SnapshotType.BASELINE, entry)
                    ));
        }
    }

    private void captureRefreshSnapshot(RaceParticipant participant, Instant now) {
        riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid())
                .ifPresent(entry -> rankSnapshotRepository.save(
                        toSnapshot(participant.getId(), now, RankSnapshot.SnapshotType.REFRESH, entry)
                ));
    }

    private void syncMatches(Race race, RaceParticipant participant) {
        long startEpochSeconds = race.getStartAt().getEpochSecond();
        List<String> matchIds = riotMatchClient.getRankedSoloMatchIdsSince(
                participant.getRiotPuuid(),
                startEpochSeconds
        );

        int imported = 0;
        for (String matchId : matchIds) {
            if (imported >= MAX_NEW_MATCHES_PER_REFRESH) {
                break;
            }
            if (participantMatchRepository.existsByParticipantIdAndRiotMatchId(participant.getId(), matchId)) {
                continue;
            }

            RiotMatchDetailDto match = riotMatchClient.getMatch(matchId);
            Instant gameStart = Instant.ofEpochMilli(match.info().gameStartTimestamp());

            if (gameStart.isBefore(race.getStartAt())) {
                continue;
            }
            if (match.info().queueId() != RiotMatchClient.RANKED_SOLO_QUEUE_ID) {
                continue;
            }

            persistMatchIfNeeded(match, gameStart);

            boolean win = match.info().participants().stream()
                    .filter(part -> participant.getRiotPuuid().equals(part.puuid()))
                    .findFirst()
                    .map(participantData -> {
                        participantProfileService.updateProfileIconIfMissing(
                                participant.getId(),
                                participantData.profileIcon()
                        );
                        return participantData.win();
                    })
                    .orElse(false);

            participantMatchRepository.save(
                    RaceParticipantMatch.create(race.getId(), participant.getId(), matchId, win)
            );
            imported++;
        }
    }

    private void persistMatchIfNeeded(RiotMatchDetailDto match, Instant gameStart) {
        String matchId = match.metadata().matchId();
        if (!riotMatchRepository.existsByRiotMatchId(matchId)) {
            riotMatchRepository.save(
                    RiotMatch.create(matchId, match.info().queueId(), gameStart)
            );
        }
    }

    private RankSnapshot toSnapshot(
            UUID participantId,
            Instant capturedAt,
            RankSnapshot.SnapshotType snapshotType,
            RiotLeagueEntryDto entry
    ) {
        return RankSnapshot.create(
                participantId,
                capturedAt,
                snapshotType,
                entry.queueType(),
                entry.tier(),
                entry.rank(),
                entry.leaguePoints(),
                entry.wins(),
                entry.losses()
        );
    }
}
