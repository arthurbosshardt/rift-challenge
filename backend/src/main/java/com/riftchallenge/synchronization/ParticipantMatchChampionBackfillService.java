package com.riftchallenge.synchronization;

import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantMatchChampionBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantMatchChampionBackfillService.class);

    static final int BATCH_SIZE = 100;

    private final ChallengeParticipantRepository participantRepository;
    private final ChallengeParticipantMatchRepository participantMatchRepository;
    private final RiotMatchLookupService riotMatchLookupService;

    public ParticipantMatchChampionBackfillService(
            ChallengeParticipantRepository participantRepository,
            ChallengeParticipantMatchRepository participantMatchRepository,
            RiotMatchLookupService riotMatchLookupService
    ) {
        this.participantRepository = participantRepository;
        this.participantMatchRepository = participantMatchRepository;
        this.riotMatchLookupService = riotMatchLookupService;
    }

    public int backfillAll() {
        long missing = participantMatchRepository.countByChampionIdIsNull();
        if (missing == 0) {
            log.info("Champion ID backfill: nothing to update");
            return 0;
        }

        log.info("Champion ID backfill: {} participant-match rows missing champion_id", missing);
        int updated = 0;

        while (true) {
            List<ChallengeParticipantMatch> batch = participantMatchRepository.findAllMissingChampionId(
                    PageRequest.of(0, BATCH_SIZE)
            );
            if (batch.isEmpty()) {
                break;
            }
            updated += backfillBatch(batch);
        }

        log.info("Champion ID backfill complete: {} rows updated", updated);
        return updated;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void backfillForParticipant(UUID participantId) {
        ChallengeParticipant participant = participantRepository.findById(participantId).orElse(null);
        if (participant == null) {
            return;
        }

        while (true) {
            List<ChallengeParticipantMatch> batch = participantMatchRepository.findMissingChampionIdByParticipantId(
                    participantId,
                    PageRequest.of(0, BATCH_SIZE)
            );
            if (batch.isEmpty()) {
                return;
            }
            backfillBatchForParticipant(participant, batch);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void backfillMatchIfMissing(ChallengeParticipant participant, String matchId) {
        participantMatchRepository.findByParticipantIdAndRiotMatchId(participant.getId(), matchId)
                .filter(link -> link.getChampionId() == null)
                .ifPresent(link -> backfillMatchLinksForParticipant(participant, matchId, List.of(link)));
    }

    private int backfillBatch(List<ChallengeParticipantMatch> links) {
        Map<String, List<ChallengeParticipantMatch>> linksByMatchId = groupByMatchId(links);
        int updated = 0;

        for (Map.Entry<String, List<ChallengeParticipantMatch>> entry : linksByMatchId.entrySet()) {
            updated += backfillMatchLinks(entry.getKey(), entry.getValue());
        }

        return updated;
    }

    private int backfillBatchForParticipant(ChallengeParticipant participant, List<ChallengeParticipantMatch> links) {
        Map<String, List<ChallengeParticipantMatch>> linksByMatchId = groupByMatchId(links);
        int updated = 0;

        for (Map.Entry<String, List<ChallengeParticipantMatch>> entry : linksByMatchId.entrySet()) {
            updated += backfillMatchLinksForParticipant(participant, entry.getKey(), entry.getValue());
        }

        return updated;
    }

    private int backfillMatchLinks(String matchId, List<ChallengeParticipantMatch> links) {
        try {
            RiotMatchDetailDto match = riotMatchLookupService.getMatch(matchId);
            int updated = 0;

            for (ChallengeParticipantMatch link : links) {
                ChallengeParticipant participant = participantRepository.findById(link.getParticipantId()).orElse(null);
                if (participant == null) {
                    continue;
                }
                if (persistChampionId(participant, link, match)) {
                    updated++;
                }
            }

            return updated;
        } catch (RuntimeException ex) {
            log.debug("Champion ID backfill skipped for match {}: {}", matchId, ex.getMessage());
            return 0;
        }
    }

    private int backfillMatchLinksForParticipant(
            ChallengeParticipant participant,
            String matchId,
            List<ChallengeParticipantMatch> links
    ) {
        try {
            RiotMatchDetailDto match = riotMatchLookupService.getMatch(matchId);
            int updated = 0;

            for (ChallengeParticipantMatch link : links) {
                if (persistChampionId(participant, link, match)) {
                    updated++;
                }
            }

            return updated;
        } catch (RuntimeException ex) {
            log.debug("Champion ID backfill skipped for match {}: {}", matchId, ex.getMessage());
            return 0;
        }
    }

    private boolean persistChampionId(
            ChallengeParticipant participant,
            ChallengeParticipantMatch link,
            RiotMatchDetailDto match
    ) {
        Integer championId = extractChampionId(match, participant.getRiotPuuid());
        if (championId == null) {
            return false;
        }

        link.updateChampionId(championId);
        participantMatchRepository.save(link);
        return true;
    }

    private static Map<String, List<ChallengeParticipantMatch>> groupByMatchId(List<ChallengeParticipantMatch> links) {
        Map<String, List<ChallengeParticipantMatch>> grouped = new LinkedHashMap<>();

        for (ChallengeParticipantMatch link : links) {
            grouped.computeIfAbsent(link.getRiotMatchId(), ignored -> new ArrayList<>()).add(link);
        }

        return grouped;
    }

    static Integer extractChampionId(RiotMatchDetailDto match, String puuid) {
        for (RiotMatchDetailDto.Participant part : match.info().participants()) {
            if (!puuid.equals(part.puuid())) {
                continue;
            }
            if (part.championId() != null && part.championId() > 0) {
                return part.championId();
            }
            return null;
        }
        return null;
    }
}
