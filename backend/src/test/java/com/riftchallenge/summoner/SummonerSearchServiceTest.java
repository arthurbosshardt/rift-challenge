package com.riftchallenge.summoner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.TestRiotAccounts;
import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SummonerSearchServiceTest {

    @Mock
    private ChallengeParticipantRepository participantRepository;
    @Mock
    private RiotAccountRepository riotAccountRepository;
    @Mock
    private PlayerLookupService playerLookupService;
    @Mock
    private SummonerSearchRiotFallbackThrottle riotFallbackThrottle;

    @InjectMocks
    private SummonerSearchService summonerSearchService;

    @Test
    void search_whenQueryTooShort_returnsEmpty() {
        assertThat(summonerSearchService.search("T", null, null)).isEmpty();
        assertThat(summonerSearchService.search(" ", null, null)).isEmpty();
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

        List<SummonerSuggestionResponse> results = summonerSearchService.search("Tan", null, null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().riotId()).isEqualTo("Tanor#7154");
    }

    @Test
    void search_whenNothingLocalAndQueryIsPartialNameOnly_doesNotAttemptRiotFallback() {
        when(participantRepository.searchByRiotId(eq("Spear619"), any(Pageable.class))).thenReturn(List.of());
        when(riotAccountRepository.searchByRiotId(eq("Spear619"), any(Pageable.class))).thenReturn(List.of());

        assertThat(summonerSearchService.search("Spear619", null, null)).isEmpty();

        verify(riotFallbackThrottle, never()).tryClaim(any(), any());
        verify(playerLookupService, never()).resolve(any(), any());
    }

    @Test
    void search_whenNothingLocalAndQueryIsCompleteRiotId_fallsBackToRiot() {
        when(participantRepository.searchByRiotId(eq("Spear619#EUW"), any(Pageable.class))).thenReturn(List.of());
        when(riotAccountRepository.searchByRiotId(eq("Spear619#EUW"), any(Pageable.class))).thenReturn(List.of());
        when(riotFallbackThrottle.tryClaim(any(), any())).thenReturn(true);
        SummonerSuggestionResponse suggestion = new SummonerSuggestionResponse(
                "puuid-new", "Spear619", "EUW", "Spear619#EUW", 7
        );
        when(playerLookupService.resolve("Spear619", "EUW")).thenReturn(Optional.of(suggestion));

        List<SummonerSuggestionResponse> results = summonerSearchService.search("Spear619#EUW", null, null);

        assertThat(results).containsExactly(suggestion);
    }

    @Test
    void search_whenRiotFallbackThrottled_returnsEmptyWithoutCallingRiot() {
        when(participantRepository.searchByRiotId(eq("Spear619#EUW"), any(Pageable.class))).thenReturn(List.of());
        when(riotAccountRepository.searchByRiotId(eq("Spear619#EUW"), any(Pageable.class))).thenReturn(List.of());
        when(riotFallbackThrottle.tryClaim(any(), any())).thenReturn(false);

        assertThat(summonerSearchService.search("Spear619#EUW", null, null)).isEmpty();

        verify(playerLookupService, never()).resolve(any(), any());
    }

    @Test
    void search_whenRiotLookupFails_returnsEmptyInsteadOfPropagating() {
        when(participantRepository.searchByRiotId(eq("Spear619#EUW"), any(Pageable.class))).thenReturn(List.of());
        when(riotAccountRepository.searchByRiotId(eq("Spear619#EUW"), any(Pageable.class))).thenReturn(List.of());
        when(riotFallbackThrottle.tryClaim(any(), any())).thenReturn(true);
        when(playerLookupService.resolve("Spear619", "EUW"))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Riot API rate limit reached"));

        assertThat(summonerSearchService.search("Spear619#EUW", null, null)).isEmpty();
    }
}
