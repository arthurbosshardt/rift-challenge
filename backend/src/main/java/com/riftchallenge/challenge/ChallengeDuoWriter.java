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
 * Performs the locked, atomic write for duo creation in its own transaction, separate from any
 * Riot API call. The challenge row is locked for the duration so concurrent additions to the same
 * challenge are serialized and can't both slip past the duo-limit / duplicate checks.
 */
@Component
class ChallengeDuoWriter {

    private final ChallengeRepository challengeRepository;
    private final ChallengeDuoRepository challengeDuoRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final RiotAccountService riotAccountService;

    ChallengeDuoWriter(
            ChallengeRepository challengeRepository,
            ChallengeDuoRepository challengeDuoRepository,
            ChallengeParticipantRepository participantRepository,
            RiotAccountService riotAccountService
    ) {
        this.challengeRepository = challengeRepository;
        this.challengeDuoRepository = challengeDuoRepository;
        this.participantRepository = participantRepository;
        this.riotAccountService = riotAccountService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    DuoWriteResult addDuo(UUID challengeId, UUID ownerId, RiotAccountDto account1, RiotAccountDto account2) {
        Challenge challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        if (!challenge.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the challenge owner");
        }
        if (challenge.getType() != ChallengeType.DUOQ) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duos can only be added to DuoQ challenges");
        }
        if (challengeDuoRepository.countByChallengeId(challengeId) >= ChallengeDuoService.MAX_DUOS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duo limit reached");
        }
        if (participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, account1.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Player already added");
        }
        if (participantRepository.existsByChallengeIdAndRiotPuuid(challengeId, account2.puuid())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Player already added");
        }

        riotAccountService.findOrCreate(account1, null);
        riotAccountService.findOrCreate(account2, null);

        ChallengeDuo duo = challengeDuoRepository.save(ChallengeDuo.create(challengeId));
        ChallengeParticipant participant1 = participantRepository.save(
                ChallengeParticipant.create(challengeId, account1, duo.getId())
        );
        ChallengeParticipant participant2 = participantRepository.save(
                ChallengeParticipant.create(challengeId, account2, duo.getId())
        );

        return new DuoWriteResult(challenge, participant1, participant2);
    }

    record DuoWriteResult(Challenge challenge, ChallengeParticipant participant1, ChallengeParticipant participant2) {
    }
}
