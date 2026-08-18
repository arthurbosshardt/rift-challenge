package com.riftchallenge.synchronization;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.challenge.ChallengeRefreshRecordService;
import com.riftchallenge.challenge.ChallengeRefreshRepository;
import com.riftchallenge.challenge.ChallengeRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChallengeSyncService {

    public static final Duration REFRESH_COOLDOWN = Duration.ofMinutes(2);

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeRefreshRepository challengeRefreshRepository;
    private final ChallengeParticipantSyncService participantSyncService;
    private final ChallengeRefreshRecordService refreshRecordService;
    private final Clock clock;

    public ChallengeSyncService(
            ChallengeRepository challengeRepository,
            ChallengeParticipantRepository participantRepository,
            ChallengeRefreshRepository challengeRefreshRepository,
            ChallengeParticipantSyncService participantSyncService,
            ChallengeRefreshRecordService refreshRecordService,
            Clock clock
    ) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.challengeRefreshRepository = challengeRefreshRepository;
        this.participantSyncService = participantSyncService;
        this.refreshRecordService = refreshRecordService;
        this.clock = clock;
    }

    public Instant refreshChallenge(UUID challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        Instant now = clock.instant();
        if (now.isBefore(challenge.getStartAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Challenge has not started yet");
        }

        enforceCooldown(challengeId, now);

        List<ChallengeParticipant> participants = participantRepository.findByChallengeIdOrderByCreatedAtAsc(challenge.getId());
        boolean rateLimited = false;

        for (ChallengeParticipant participant : participants) {
            try {
                participantSyncService.syncParticipant(challenge, participant, now);
            } catch (ResponseStatusException exception) {
                if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    rateLimited = true;
                    continue;
                }
                throw exception;
            }
        }

        refreshRecordService.recordRefresh(challengeId, now);

        if (rateLimited) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Riot API rate limit reached; partial sync saved"
            );
        }

        return now;
    }

    private void enforceCooldown(UUID challengeId, Instant now) {
        challengeRefreshRepository.findByChallengeId(challengeId).ifPresent(refresh -> {
            Instant nextAllowed = refresh.getRefreshedAt().plus(REFRESH_COOLDOWN);
            if (now.isBefore(nextAllowed)) {
                throw new ResponseStatusException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Refresh available at " + nextAllowed
                );
            }
        });
    }
}
