package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.AddParticipantRequest;
import com.riftchallenge.challenge.dto.ParticipantResponse;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotIdParser;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChallengeParticipantService {

    static final int MAX_PARTICIPANTS = 16;

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final BaselineSnapshotService baselineSnapshotService;
    private final RiotAccountClient riotAccountClient;
    private final ParticipantProfileService participantProfileService;
    private final ChallengeParticipantWriter participantWriter;
    private final Clock clock;

    public ChallengeParticipantService(
            ChallengeRepository challengeRepository,
            ChallengeParticipantRepository participantRepository,
            BaselineSnapshotService baselineSnapshotService,
            RiotAccountClient riotAccountClient,
            ParticipantProfileService participantProfileService,
            ChallengeParticipantWriter participantWriter,
            Clock clock
    ) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.baselineSnapshotService = baselineSnapshotService;
        this.riotAccountClient = riotAccountClient;
        this.participantProfileService = participantProfileService;
        this.participantWriter = participantWriter;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> listByChallengeId(UUID challengeId) {
        return participantRepository.findByChallengeIdOrderByCreatedAtAsc(challengeId).stream()
                .map(ParticipantResponse::from)
                .toList();
    }

    public ParticipantResponse addParticipant(UUID challengeId, UUID ownerId, AddParticipantRequest request) {
        Challenge preCheck = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        if (!preCheck.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the challenge owner");
        }

        if (preCheck.getType() == ChallengeType.DUOQ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the duo endpoint for DuoQ challenges");
        }

        if (participantRepository.countByChallengeId(challengeId) >= MAX_PARTICIPANTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participant limit reached");
        }

        RiotIdParser.ParsedRiotId parsed = RiotIdParser.parse(request.riotId());
        RiotAccountDto account = riotAccountClient.getAccountByRiotId(parsed.gameName(), parsed.tagLine());

        if (participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, account.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Participant already added");
        }

        ChallengeParticipantWriter.ParticipantWriteResult result =
                participantWriter.addParticipant(challengeId, ownerId, account);
        ChallengeParticipant saved = result.participant();
        Challenge challenge = result.challenge();

        if (clock.instant().isBefore(challenge.getStartAt())) {
            baselineSnapshotService.captureBaselineIfRanked(saved, challenge.getRegion());
        }
        participantProfileService.ensureProfileIcon(saved.getId(), challenge.getRegion());
        return ParticipantResponse.from(saved);
    }

    @Transactional
    public void removeParticipant(UUID challengeId, UUID participantId, UUID ownerId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        if (!challenge.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the challenge owner");
        }

        ChallengeParticipant participant = participantRepository.findByIdAndChallengeId(participantId, challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));

        participantRepository.delete(participant);
    }
}
