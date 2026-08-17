package com.riftrace.race;

import com.riftrace.race.dto.AddDuoRequest;
import com.riftrace.riot.RiotAccountClient;
import com.riftrace.riot.RiotIdParser;
import com.riftrace.riot.RiotLeagueClient;
import com.riftrace.riot.dto.RiotAccountDto;
import com.riftrace.riot.dto.RiotLeagueEntryDto;
import com.riftrace.synchronization.RankSnapshot;
import com.riftrace.synchronization.RankSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RaceDuoService {

    static final int MAX_DUOS = 8;

    private final RaceRepository raceRepository;
    private final RaceDuoRepository raceDuoRepository;
    private final RaceParticipantRepository participantRepository;
    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotAccountClient riotAccountClient;
    private final RiotLeagueClient riotLeagueClient;
    private final ParticipantProfileService participantProfileService;
    private final Clock clock;

    public RaceDuoService(
            RaceRepository raceRepository,
            RaceDuoRepository raceDuoRepository,
            RaceParticipantRepository participantRepository,
            RankSnapshotRepository rankSnapshotRepository,
            RiotAccountClient riotAccountClient,
            RiotLeagueClient riotLeagueClient,
            ParticipantProfileService participantProfileService,
            Clock clock
    ) {
        this.raceRepository = raceRepository;
        this.raceDuoRepository = raceDuoRepository;
        this.participantRepository = participantRepository;
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotAccountClient = riotAccountClient;
        this.riotLeagueClient = riotLeagueClient;
        this.participantProfileService = participantProfileService;
        this.clock = clock;
    }

    @Transactional
    public void addDuo(UUID raceId, UUID ownerId, AddDuoRequest request) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Race not found"));

        if (!race.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the race owner");
        }

        if (race.getType() != RaceType.DUOQ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duos can only be added to DuoQ races");
        }

        if (raceDuoRepository.countByRaceId(raceId) >= MAX_DUOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duo limit reached");
        }

        RiotIdParser.ParsedRiotId parsed1 = RiotIdParser.parse(request.player1RiotId());
        RiotIdParser.ParsedRiotId parsed2 = RiotIdParser.parse(request.player2RiotId());

        if (parsed1.gameName().equalsIgnoreCase(parsed2.gameName())
                && parsed1.tagLine().equalsIgnoreCase(parsed2.tagLine())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A duo must contain two different players");
        }

        RiotAccountDto account1 = riotAccountClient.getAccountByRiotId(parsed1.gameName(), parsed1.tagLine());
        RiotAccountDto account2 = riotAccountClient.getAccountByRiotId(parsed2.gameName(), parsed2.tagLine());

        if (participantRepository.existsByRaceIdAndRiotPuuid(raceId, account1.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Player already added");
        }
        if (participantRepository.existsByRaceIdAndRiotPuuid(raceId, account2.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Player already added");
        }

        RaceDuo duo = raceDuoRepository.save(RaceDuo.create(raceId));

        RaceParticipant participant1 = participantRepository.save(
                RaceParticipant.create(raceId, account1, duo.getId())
        );
        RaceParticipant participant2 = participantRepository.save(
                RaceParticipant.create(raceId, account2, duo.getId())
        );

        if (clock.instant().isBefore(race.getStartAt())) {
            captureBaselineIfRanked(participant1);
            captureBaselineIfRanked(participant2);
        }
        participantProfileService.ensureProfileIcon(participant1.getId());
        participantProfileService.ensureProfileIcon(participant2.getId());
    }

    @Transactional
    public void removeDuo(UUID raceId, UUID duoId, UUID ownerId) {
        Race race = raceRepository.findById(raceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Race not found"));

        if (!race.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the race owner");
        }

        RaceDuo duo = raceDuoRepository.findById(duoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Duo not found"));

        if (!duo.getRaceId().equals(raceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Duo not found");
        }

        raceDuoRepository.delete(duo);
    }

    private void captureBaselineIfRanked(RaceParticipant participant) {
        try {
            riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid())
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
