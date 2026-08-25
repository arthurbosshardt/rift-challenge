package com.riftchallenge.challenge;

import com.riftchallenge.account.RiotAccountService;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Performs the locked, atomic write for participant creation in its own transaction, separate
 * from any Riot API call. The challenge row is locked for the duration so concurrent additions
 * to the same challenge are serialized and can't both slip past the participant-limit / duplicate
 * checks.
 */
@Component
class ChallengeParticipantWriter {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final RiotAccountService riotAccountService;

    ChallengeParticipantWriter(
            ChallengeRepository challengeRepository,
            ChallengeParticipantRepository participantRepository,
            RiotAccountService riotAccountService
    ) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.riotAccountService = riotAccountService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    ParticipantWriteResult addParticipant(UUID challengeId, UUID ownerId, RiotAccountDto account) {
        Challenge challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        if (!challenge.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the challenge owner");
        }
        if (challenge.getType() == ChallengeType.DUOQ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the duo endpoint for DuoQ challenges");
        }
        if (participantRepository.countByChallengeId(challengeId) >= ChallengeParticipantService.MAX_PARTICIPANTS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Participant limit reached");
        }
        if (participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, account.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Participant already added");
        }

        riotAccountService.findOrCreate(account, null);

        ChallengeParticipant saved = participantRepository.save(ChallengeParticipant.create(challengeId, account));
        return new ParticipantWriteResult(challenge, saved);
    }

    record ParticipantWriteResult(Challenge challenge, ChallengeParticipant participant) {
    }
}
