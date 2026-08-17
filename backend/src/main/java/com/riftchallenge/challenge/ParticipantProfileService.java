package com.riftchallenge.challenge;

import com.riftchallenge.riot.RiotSummonerClient;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ParticipantProfileService {

    private final RiotSummonerClient riotSummonerClient;
    private final ChallengeParticipantRepository participantRepository;

    public ParticipantProfileService(
            RiotSummonerClient riotSummonerClient,
            ChallengeParticipantRepository participantRepository
    ) {
        this.riotSummonerClient = riotSummonerClient;
        this.participantRepository = participantRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureProfileIcon(UUID participantId) {
        participantRepository.findById(participantId).ifPresent(this::ensureProfileIconLoaded);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateProfileIconIfMissing(UUID participantId, Integer profileIconId) {
        if (profileIconId == null) {
            return;
        }

        participantRepository.findById(participantId).ifPresent(participant -> {
            if (participant.getProfileIconId() == null) {
                participant.updateProfileIconId(profileIconId);
                participantRepository.save(participant);
            }
        });
    }

    private void ensureProfileIconLoaded(ChallengeParticipant participant) {
        if (participant.getProfileIconId() != null) {
            return;
        }

        try {
            riotSummonerClient.findProfileIconId(participant.getRiotPuuid())
                    .ifPresent(iconId -> {
                        participant.updateProfileIconId(iconId);
                        participantRepository.save(participant);
                    });
        } catch (ResponseStatusException ignored) {
            // Profile icon can be loaded on refresh if Riot is temporarily unavailable.
        }
    }
}
