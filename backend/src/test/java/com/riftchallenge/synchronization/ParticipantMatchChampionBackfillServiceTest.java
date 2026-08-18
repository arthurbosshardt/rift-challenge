package com.riftchallenge.synchronization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ParticipantMatchChampionBackfillServiceTest {

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private ChallengeParticipantMatchRepository participantMatchRepository;

    @Mock
    private RiotMatchLookupService riotMatchLookupService;

    @InjectMocks
    private ParticipantMatchChampionBackfillService backfillService;

    @Test
    void backfillAll_groupsLinksByMatchId() {
        ChallengeParticipant participant1 = participant("puuid-1");
        ChallengeParticipant participant2 = participant("puuid-2");

        ChallengeParticipantMatch link1 = link(participant1.getId(), "EUW1_1");
        ChallengeParticipantMatch link2 = link(participant2.getId(), "EUW1_1");

        when(participantMatchRepository.countByChampionIdIsNull()).thenReturn(2L);
        when(participantMatchRepository.findAllMissingChampionId(any(Pageable.class)))
                .thenReturn(List.of(link1, link2))
                .thenReturn(List.of());
        when(participantRepository.findById(participant1.getId())).thenReturn(Optional.of(participant1));
        when(participantRepository.findById(participant2.getId())).thenReturn(Optional.of(participant2));
        when(riotMatchLookupService.getMatch("EUW1_1")).thenReturn(match(
                matchParticipant("puuid-1", 103),
                matchParticipant("puuid-2", 86)
        ));

        int updated = backfillService.backfillAll();

        assertThat(updated).isEqualTo(2);
        verify(riotMatchLookupService).getMatch("EUW1_1");

        ArgumentCaptor<ChallengeParticipantMatch> saved = ArgumentCaptor.forClass(ChallengeParticipantMatch.class);
        verify(participantMatchRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ChallengeParticipantMatch::getChampionId)
                .containsExactlyInAnyOrder(103, 86);
    }

    @Test
    void backfillAll_returnsZeroWhenNothingMissing() {
        when(participantMatchRepository.countByChampionIdIsNull()).thenReturn(0L);

        assertThat(backfillService.backfillAll()).isZero();
        verify(participantMatchRepository, never()).findAllMissingChampionId(any(Pageable.class));
    }

    @Test
    void extractChampionId_readsParticipantChampionId() {
        RiotMatchDetailDto match = match(matchParticipant("target-puuid", 157));

        assertThat(ParticipantMatchChampionBackfillService.extractChampionId(match, "target-puuid"))
                .isEqualTo(157);
    }

    private static ChallengeParticipant participant(String puuid) {
        return ChallengeParticipant.create(
                UUID.randomUUID(),
                new com.riftchallenge.riot.dto.RiotAccountDto(puuid, "Player", "EUW")
        );
    }

    private static ChallengeParticipantMatch link(UUID participantId, String matchId) {
        return ChallengeParticipantMatch.create(UUID.randomUUID(), participantId, matchId, true, null);
    }

    private static RiotMatchDetailDto match(RiotMatchDetailDto.Participant... participants) {
        return new RiotMatchDetailDto(
                new RiotMatchDetailDto.Metadata("EUW1_1"),
                new RiotMatchDetailDto.Info(
                        1_700_000_000_000L,
                        420,
                        List.of(participants)
                )
        );
    }

    private static RiotMatchDetailDto.Participant matchParticipant(String puuid, int championId) {
        return new RiotMatchDetailDto.Participant(
                puuid,
                true,
                1,
                championId,
                "Ahri"
        );
    }
}
