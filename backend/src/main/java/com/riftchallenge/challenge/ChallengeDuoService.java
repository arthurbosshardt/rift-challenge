package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.AddDuoRequest;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotIdParser;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.time.Clock;
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
    private final BaselineSnapshotService baselineSnapshotService;
    private final RiotAccountClient riotAccountClient;
    private final ParticipantProfileService participantProfileService;
    private final ChallengeDuoWriter duoWriter;
    private final Clock clock;

    public ChallengeDuoService(
            ChallengeRepository challengeRepository,
            ChallengeDuoRepository challengeDuoRepository,
            ChallengeParticipantRepository participantRepository,
            BaselineSnapshotService baselineSnapshotService,
            RiotAccountClient riotAccountClient,
            ParticipantProfileService participantProfileService,
            ChallengeDuoWriter duoWriter,
            Clock clock
    ) {
        this.challengeRepository = challengeRepository;
        this.challengeDuoRepository = challengeDuoRepository;
        this.participantRepository = participantRepository;
        this.baselineSnapshotService = baselineSnapshotService;
        this.riotAccountClient = riotAccountClient;
        this.participantProfileService = participantProfileService;
        this.duoWriter = duoWriter;
        this.clock = clock;
    }

    public void addDuo(UUID challengeId, UUID ownerId, AddDuoRequest request) {
        Challenge preCheck = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        if (!preCheck.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the challenge owner");
        }

        if (preCheck.getType() != ChallengeType.DUOQ) {
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

        RiotAccountDto account1 = requireRiotAccount(parsed1, "player 1");
        RiotAccountDto account2 = requireRiotAccount(parsed2, "player 2");

        if (participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, account1.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Player already added");
        }
        if (participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, account2.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Player already added");
        }

        ChallengeDuoWriter.DuoWriteResult result = duoWriter.addDuo(challengeId, ownerId, account1, account2);
        Challenge challenge = result.challenge();

        if (clock.instant().isBefore(challenge.getStartAt())) {
            baselineSnapshotService.captureBaselineIfRanked(result.participant1(), challenge.getRegion());
            baselineSnapshotService.captureBaselineIfRanked(result.participant2(), challenge.getRegion());
        }
        participantProfileService.ensureProfileIcon(result.participant1().getId(), challenge.getRegion());
        participantProfileService.ensureProfileIcon(result.participant2().getId(), challenge.getRegion());
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

    private RiotAccountDto requireRiotAccount(RiotIdParser.ParsedRiotId parsed, String playerLabel) {
        try {
            return riotAccountClient.getAccountByRiotId(parsed.gameName(), parsed.tagLine());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Riot account not found for " + playerLabel
                );
            }
            throw exception;
        }
    }
}
