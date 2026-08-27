package com.riftchallenge.synchronization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.leaderboard.AccountMatch;
import com.riftchallenge.leaderboard.AccountMatchRepository;
import com.riftchallenge.riot.RiotMatchLookupService;
import com.riftchallenge.riot.dto.RiotMatchDetailDto;
import java.util.List;
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
    private AccountMatchRepository accountMatchRepository;

    @Mock
    private RiotMatchLookupService riotMatchLookupService;

    @InjectMocks
    private ParticipantMatchChampionBackfillService backfillService;

    @Test
    void backfillAll_groupsLinksByMatchId() {
        AccountMatch link1 = link("puuid-1", "EUW1_1");
        AccountMatch link2 = link("puuid-2", "EUW1_1");

        when(accountMatchRepository.countByChampionIdIsNull()).thenReturn(2L);
        when(accountMatchRepository.findAllMissingChampionId(any(Pageable.class)))
                .thenReturn(List.of(link1, link2))
                .thenReturn(List.of());
        when(riotMatchLookupService.getMatch("EUW1_1")).thenReturn(match(
                matchParticipant("puuid-1", 103),
                matchParticipant("puuid-2", 86)
        ));

        int updated = backfillService.backfillAll();

        assertThat(updated).isEqualTo(2);
        verify(riotMatchLookupService).getMatch("EUW1_1");

        ArgumentCaptor<AccountMatch> saved = ArgumentCaptor.forClass(AccountMatch.class);
        verify(accountMatchRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(AccountMatch::getChampionId)
                .containsExactlyInAnyOrder(103, 86);
    }

    @Test
    void backfillAll_returnsZeroWhenNothingMissing() {
        when(accountMatchRepository.countByChampionIdIsNull()).thenReturn(0L);

        assertThat(backfillService.backfillAll()).isZero();
        verify(accountMatchRepository, never()).findAllMissingChampionId(any(Pageable.class));
    }

    @Test
    void extractChampionId_readsParticipantChampionId() {
        RiotMatchDetailDto match = match(matchParticipant("target-puuid", 157));

        assertThat(ParticipantMatchChampionBackfillService.extractChampionId(match, "target-puuid"))
                .isEqualTo(157);
    }

    private static AccountMatch link(String riotPuuid, String matchId) {
        return AccountMatch.create(riotPuuid, matchId, true, null, null, 0, 0, 0, 0, 0L);
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
