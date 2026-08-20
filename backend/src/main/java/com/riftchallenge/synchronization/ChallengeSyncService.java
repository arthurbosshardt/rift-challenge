package com.riftchallenge.synchronization;

import com.riftchallenge.challenge.Challenge;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.challenge.ChallengeRefreshRecordService;
import com.riftchallenge.challenge.ChallengeRefreshRepository;
import com.riftchallenge.challenge.ChallengeRepository;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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
    private final RiotMatchLookupService riotMatchLookupService;
    private final ExecutorService riotSyncExecutor;
    private final Clock clock;

    public ChallengeSyncService(
            ChallengeRepository challengeRepository,
            ChallengeParticipantRepository participantRepository,
            ChallengeRefreshRepository challengeRefreshRepository,
            ChallengeParticipantSyncService participantSyncService,
            ChallengeRefreshRecordService refreshRecordService,
            RiotMatchLookupService riotMatchLookupService,
            ExecutorService riotSyncExecutor,
            Clock clock
    ) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.challengeRefreshRepository = challengeRefreshRepository;
        this.participantSyncService = participantSyncService;
        this.refreshRecordService = refreshRecordService;
        this.riotMatchLookupService = riotMatchLookupService;
        this.riotSyncExecutor = riotSyncExecutor;
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
        if (participants.isEmpty()) {
            return now;
        }

        boolean rateLimited = false;
        ResponseStatusException failure = null;

        riotMatchLookupService.beginRefreshScope();
        try {
            Map<String, RiotMatchDetailDto> sharedMatchCache =
                    riotMatchLookupService.currentScopeCache();

            List<Future<Boolean>> futures = new ArrayList<>();
            for (ChallengeParticipant participant : participants) {
                futures.add(riotSyncExecutor.submit(() -> syncParticipantInWorker(
                        challenge, participant, now, sharedMatchCache
                )));
            }

            for (Future<Boolean> future : futures) {
                try {
                    boolean participantRateLimited = future.get();
                    rateLimited = rateLimited || participantRateLimited;
                } catch (ExecutionException exception) {
                    if (failure == null && exception.getCause() instanceof ResponseStatusException statusException) {
                        failure = statusException;
                    } else if (failure == null) {
                        throw new RuntimeException(exception.getCause());
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(exception);
                }
            }
        } finally {
            riotMatchLookupService.endRefreshScope();
        }

        refreshRecordService.recordRefresh(challengeId, now);

        if (failure != null) {
            throw failure;
        }

        if (rateLimited) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Riot API rate limit reached; partial sync saved"
            );
        }

        return now;
    }

    private boolean syncParticipantInWorker(
            Challenge challenge,
            ChallengeParticipant participant,
            Instant now,
            Map<String, RiotMatchDetailDto> sharedMatchCache
    ) {
        riotMatchLookupService.bindScope(sharedMatchCache);
        try {
            participantSyncService.syncParticipant(challenge, participant, now);
            return false;
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return true;
            }
            throw exception;
        } finally {
            riotMatchLookupService.unbindScope();
        }
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
