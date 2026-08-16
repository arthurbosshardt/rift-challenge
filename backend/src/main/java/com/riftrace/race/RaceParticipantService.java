package com.riftrace.race;

import com.riftrace.race.dto.AddParticipantRequest;
import com.riftrace.race.dto.ParticipantResponse;
import com.riftrace.riot.RiotAccountClient;
import com.riftrace.riot.RiotIdParser;
import com.riftrace.riot.RiotLeagueClient;
import com.riftrace.riot.dto.RiotAccountDto;
import com.riftrace.riot.dto.RiotLeagueEntryDto;
import com.riftrace.synchronization.RankSnapshot;
import com.riftrace.synchronization.RankSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RaceParticipantService {

    static final int MAX_PARTICIPANTS = 16;

    private final RaceRepository raceRepository;
    private final RaceParticipantRepository participantRepository;
    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotAccountClient riotAccountClient;
    private final RiotLeagueClient riotLeagueClient;
    private final Clock clock;

    public RaceParticipantService(
            RaceRepository raceRepository,
            RaceParticipantRepository participantRepository,
            RankSnapshotRepository rankSnapshotRepository,
            RiotAccountClient riotAccountClient,
            RiotLeagueClient riotLeagueClient,
            Clock clock
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotAccountClient = riotAccountClient;
        this.riotLeagueClient = riotLeagueClient;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> listByRaceId(UUID raceId) {
        return participantRepository.findByRaceIdOrderByCreatedAtAsc(raceId).stream()
                .map(ParticipantResponse::from)
                .toList();
    }

    @Transactional
    public ParticipantResponse addParticipant(UUID raceId, UUID ownerId, AddParticipantRequest request) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Race not found"));

        if (!race.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the race owner");
        }

        if (participantRepository.countByRaceId(raceId) >= MAX_PARTICIPANTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participant limit reached");
        }

        RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse(request.riotId());
        RiotAccountDto account = riotAccountClient.getAccountByRiotId(parsed.gameName(), parsed.tagLine());

        if (participantRepository.existsByRaceIdAndRiotPuuid(raceId, account.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Participant already added");
        }

        RaceParticipant participant = RaceParticipant.create(raceId, account);
        RaceParticipant saved = participantRepository.save(participant);
        captureBaselineIfRanked(saved);
        return ParticipantResponse.from(saved);
    }

    @Transactional
    public void removeParticipant(UUID raceId, UUID participantId, UUID ownerId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Race not found"));

        if (!race.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the race owner");
        }

        RaceParticipant participant = participantRepository.findByIdAndRaceId(participantId, raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));

        participantRepository.delete(participant);
    }

    private void captureBaselineIfRanked(RaceParticipant participant) {
        riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid())
                .ifPresent(entry -> rankSnapshotRepository.save(toBaselineSnapshot(participant.getId(), entry)));
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
