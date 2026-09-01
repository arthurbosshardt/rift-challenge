package com.riftchallenge.synchronization;

import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.leaderboard.AccountMatch;
import com.riftchallenge.leaderboard.AccountMatchRepository;
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

@Service
public class ParticipantMatchChampionBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantMatchChampionBackfillService.class);

    static final int BATCH_SIZE = 100;
    /**
     * Cap for {@link #backfillForParticipant}, which — unlike {@link #backfillAll} (a one-off
     * startup job) — runs synchronously on the request thread of every active-challenge refresh.
     * It backfills champion_id for a participant's whole match history, not just this challenge's
     * window, so on an account with a large backlog (e.g. rows migrated by V33 without champion
     * data) an unbounded loop here could block a refresh for as long as it takes to work through
     * the entire backlog, one Riot call per match. A refresh chips away at it incrementally
     * instead, same tradeoff as the other per-refresh import caps in ChallengeParticipantSyncService.
     */
    static final int MAX_MATCHES_PER_PARTICIPANT_REFRESH = 15;

    private final ChallengeParticipantRepository participantRepository;
    private final AccountMatchRepository accountMatchRepository;
    private final RiotMatchLookupService riotMatchLookupService;

    public ParticipantMatchChampionBackfillService(
            ChallengeParticipantRepository participantRepository,
            AccountMatchRepository accountMatchRepository,
            RiotMatchLookupService riotMatchLookupService
    ) {
        this.participantRepository = participantRepository;
        this.accountMatchRepository = accountMatchRepository;
        this.riotMatchLookupService = riotMatchLookupService;
    }

    public int backfillAll() {
        long missing = accountMatchRepository.countByChampionIdIsNull();
        if (missing == 0) {
            log.info("Champion ID backfill: nothing to update");
            return 0;
        }

        log.info("Champion ID backfill: {} account-match rows missing champion_id", missing);
        int updated = 0;

        while (true) {
            List<AccountMatch> batch = accountMatchRepository.findAllMissingChampionId(
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

    // Intentionally not @Transactional: backfillMatchLinks() below calls out to Riot per match.
    // Each repository read/write already runs its own short-lived transaction (Spring Data
    // default), so no Hikari connection is held open across the network calls in this loop.
    public void backfillForParticipant(UUID participantId) {
        ChallengeParticipant participant = participantRepository.findById(participantId).orElse(null);
        if (participant == null) {
            return;
        }

        List<AccountMatch> batch = accountMatchRepository.findMissingChampionIdByRiotPuuid(
                participant.getRiotPuuid(),
                PageRequest.of(0, MAX_MATCHES_PER_PARTICIPANT_REFRESH)
        );
        if (batch.isEmpty()) {
            return;
        }
        backfillBatch(batch);
    }

    // See backfillForParticipant() above: intentionally not @Transactional.
    public void backfillMatchIfMissing(ChallengeParticipant participant, String matchId) {
        accountMatchRepository.findByRiotPuuidAndRiotMatchId(participant.getRiotPuuid(), matchId)
                .filter(link -> link.getChampionId() == null)
                .ifPresent(link -> backfillMatchLinks(matchId, List.of(link)));
    }

    private int backfillBatch(List<AccountMatch> links) {
        Map<String, List<AccountMatch>> linksByMatchId = groupByMatchId(links);
        int updated = 0;

        for (Map.Entry<String, List<AccountMatch>> entry : linksByMatchId.entrySet()) {
            updated += backfillMatchLinks(entry.getKey(), entry.getValue());
        }

        return updated;
    }

    private int backfillMatchLinks(String matchId, List<AccountMatch> links) {
        try {
            RiotMatchDetailDto match = riotMatchLookupService.getMatch(matchId);
            int updated = 0;

            for (AccountMatch link : links) {
                if (persistChampionId(link, match)) {
                    updated++;
                }
            }

            return updated;
        } catch (RuntimeException ex) {
            log.debug("Champion ID backfill skipped for match {}: {}", matchId, ex.getMessage());
            return 0;
        }
    }

    private boolean persistChampionId(AccountMatch link, RiotMatchDetailDto match) {
        Integer championId = extractChampionId(match, link.getRiotPuuid());
        if (championId == null) {
            return false;
        }

        link.updateChampionId(championId);
        accountMatchRepository.save(link);
        return true;
    }

    private static Map<String, List<AccountMatch>> groupByMatchId(List<AccountMatch> links) {
        Map<String, List<AccountMatch>> grouped = new LinkedHashMap<>();

        for (AccountMatch link : links) {
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
