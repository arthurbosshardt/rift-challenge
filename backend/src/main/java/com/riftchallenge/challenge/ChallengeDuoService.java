package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.AddDuoRequest;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotIdParser;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import com.riftchallenge.synchronization.RankSnapshot;
import com.riftchallenge.synchronization.RankSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChallengeDuoService {

    static final int MAX_DUOS = 8;

    private final ChallengeRepository challengeRepository;
    private final ChallengeDuoRepository challengeDuoRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotAccountClient riotAccountClient;
    private final RiotLeagueClient riotLeagueClient;
    private final ParticipantProfileService participantProfileService;
    private final Clock clock;

    public ChallengeDuoService(
            ChallengeRepository challengeRepository,
            ChallengeDuoRepository challengeDuoRepository,
            ChallengeParticipantRepository participantRepository,
            RankSnapshotRepository rankSnapshotRepository,
            RiotAccountClient riotAccountClient,
            RiotLeagueClient riotLeagueClient,
            ParticipantProfileService participantProfileService,
            Clock clock
    ) {
        this.challengeRepository = challengeRepository;
        this.challengeDuoRepository = challengeDuoRepository;
        this.participantRepository = participantRepository;
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotAccountClient = riotAccountClient;
        this.riotLeagueClient = riotLeagueClient;
        this.participantProfileService = participantProfileService;
        this.clock = clock;
    }

    @Transactional
    public void addDuo(UUID challengeId, UUID ownerId, AddDuoRequest request) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        if (!challenge.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the challenge owner");
        }

        if (challenge.getType() != ChallengeType.DUOQ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duos can only be added to DuoQ challenges");
        }

        if (challengeDuoRepository.countByChallengeId(challengeId) >= MAX_DUOS) {
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

        if (participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, account1.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Player already added");
        }
        if (participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, account2.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Player already added");
        }

        ChallengeDuo duo = challengeDuoRepository.save(ChallengeDuo.create(challengeId));

        ChallengeParticipant participant1 = participantRepository.save(
                ChallengeParticipant.create(challengeId, account1, duo.getId())
        );
        ChallengeParticipant participant2 = participantRepository.save(
                ChallengeParticipant.create(challengeId, account2, duo.getId())
        );

        if (clock.instant().isBefore(challenge.getStartAt())) {
            captureBaselineIfRanked(participant1);
            captureBaselineIfRanked(participant2);
        }
        participantProfileService.ensureProfileIcon(participant1.getId());
        participantProfileService.ensureProfileIcon(participant2.getId());
    }

    @Transactional
    public void removeDuo(UUID challengeId, UUID duoId, UUID ownerId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        if (!challenge.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the challenge owner");
        }

        ChallengeDuo duo = challengeDuoRepository.findById(duoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Duo not found"));

        if (!duo.getChallengeId().equals(challengeId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Duo not found");
        }

        challengeDuoRepository.delete(duo);
    }

    private void captureBaselineIfRanked(ChallengeParticipant participant) {
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
