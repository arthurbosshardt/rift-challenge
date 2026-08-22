package com.riftchallenge.summoner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.riftchallenge.TestRiotAccounts;
import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class SummonerSearchServiceTest {

    @Mock
    private ChallengeParticipantRepository participantRepository;
    @Mock
    private RiotAccountRepository riotAccountRepository;

    @InjectMocks
    private SummonerSearchService summonerSearchService;

    @Test
    void search_whenQueryTooShort_returnsEmpty() {
        assertThat(summonerSearchService.search("T")).isEmpty();
        assertThat(summonerSearchService.search(" ")).isEmpty();
    }

    @Test
    void search_deduplicatesByPuuid() {
        ChallengeParticipant participant = ChallengeParticipant.create(
                UUID.randomUUID(),
                new RiotAccountDto("puuid-1", "Tanor", "7154")
        );
        RiotAccount account = TestRiotAccounts.riotAccount("puuid-1", "Tanor", "7154", 12);
        when(participantRepository.searchByRiotId(eq("Tan"), any(Pageable.class))).thenReturn(List.of(participant));
        when(riotAccountRepository.searchByRiotId(eq("Tan"), any(Pageable.class))).thenReturn(List.of(account));

        List<SummonerSuggestionResponse> results = summonerSearchService.search("Tan");

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().riotId()).isEqualTo("Tanor#7154");
    }
}
