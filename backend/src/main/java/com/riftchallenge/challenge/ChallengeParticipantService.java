package com.riftchallenge.challenge;

import com.riftchallenge.challenge.dto.AddParticipantRequest;
import com.riftchallenge.challenge.dto.ParticipantResponse;
import com.riftchallenge.riot.ChallengeRegion;
import com.riftchallenge.riot.RiotAccountClient;
import com.riftchallenge.riot.RiotIdParser;
import com.riftchallenge.riot.RiotLeagueClient;
import com.riftchallenge.riot.dto.RiotAccountDto;
import com.riftchallenge.riot.dto.RiotLeagueEntryDto;
import com.riftchallenge.synchronization.RankSnapshot;
import com.riftchallenge.synchronization.RankSnapshotRepository;
import java.time.Clock;
import java.time.Instant;
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
    private final RankSnapshotRepository rankSnapshotRepository;
    private final RiotAccountClient riotAccountClient;
    private final RiotLeagueClient riotLeagueClient;
    private final ParticipantProfileService participantProfileService;
    private final Clock clock;

    public ChallengeParticipantService(
            ChallengeRepository challengeRepository,
            ChallengeParticipantRepository participantRepository,
            RankSnapshotRepository rankSnapshotRepository,
            RiotAccountClient riotAccountClient,
            RiotLeagueClient riotLeagueClient,
            ParticipantProfileService participantProfileService,
            Clock clock
    ) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
        this.rankSnapshotRepository = rankSnapshotRepository;
        this.riotAccountClient = riotAccountClient;
        this.riotLeagueClient = riotLeagueClient;
        this.participantProfileService = participantProfileService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ParticipantResponse> listByChallengeId(UUID challengeId) {
        return participantRepository.findByChallengeIdOrderByCreatedAtAsc(challengeId).stream()
                .map(ParticipantResponse::from)
                .toList();
    }

    @Transactional
    public ParticipantResponse addParticipant(UUID challengeId, UUID ownerId, AddParticipantRequest request) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Challenge not found"));

        if (!challenge.getOwnerId().equals(ownerId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not the challenge owner");
        }

        if (challenge.getType() == ChallengeType.DUOQ) {
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

        ChallengeParticipant participant = ChallengeParticipant.create(challengeId, account);
        ChallengeParticipant saved = participantRepository.save(participant);
        if (clock.instant().isBefore(challenge.getStartAt())) {
            captureBaselineIfRanked(saved, challenge.getRegion());
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

    private void captureBaselineIfRanked(ChallengeParticipant participant, ChallengeRegion region) {
        try {
            riotLeagueClient.findRankedSoloEntry(participant.getRiotPuuid(), region)
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
