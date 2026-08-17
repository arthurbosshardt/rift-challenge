package com.riftrace.race;

import com.riftrace.riot.RiotSummonerClient;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantProfileService {

    private final RiotSummonerClient riotSummonerClient;
    private final RaceParticipantRepository participantRepository;

    public ParticipantProfileService(
            RiotSummonerClient riotSummonerClient,
            RaceParticipantRepository participantRepository
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

    private void ensureProfileIconLoaded(RaceParticipant participant) {
        if (participant.getProfileIconId() != null) {
            return;
        }

        riotSummonerClient.findProfileIconId(participant.getRiotPuuid())
                .ifPresent(iconId -> {
                    participant.updateProfileIconId(iconId);
                    participantRepository.save(participant);
                });
    }
}
