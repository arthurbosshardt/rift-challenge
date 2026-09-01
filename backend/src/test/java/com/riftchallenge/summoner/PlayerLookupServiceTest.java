package com.riftchallenge.summoner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.TestRiotAccounts;
import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.account.RiotAccountService;
import com.riftchallenge.challenge.ChallengeParticipant;
import com.riftchallenge.challenge.ChallengeParticipantRepository;
import com.riftchallenge.riot.dto.RiotAccountDto;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PlayerLookupServiceTest {

    @Mock
    private ChallengeParticipantRepository participantRepository;

    @Mock
    private RiotAccountRepository riotAccountRepository;

    @Mock
    private RiotAccountService riotAccountService;

    private PlayerLookupService service;

    @BeforeEach
    void setUp() {
        service = new PlayerLookupService(participantRepository, riotAccountRepository, riotAccountService);
    }

    @Test
    void resolve_unknownEverywhere_returnsEmpty() {
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("Ghost", "EUW"))
                .thenReturn(Optional.empty());
        when(riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase("Ghost", "EUW"))
                .thenReturn(Optional.empty());
        when(riotAccountService.resolveExactRiotAccount("Ghost", "EUW"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Riot account not found"));

        assertThat(service.resolve("Ghost", "EUW")).isEmpty();
    }

    /** Nobody has added this player to a challenge or looked them up before — falls back to Riot. */
    @Test
    void resolve_unknownLocallyButFoundOnRiot_returnsSuggestionAndRegistersAccount() {
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("Spear619", "EUW"))
                .thenReturn(Optional.empty());
        when(riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase("Spear619", "EUW"))
                .thenReturn(Optional.empty());
        RiotAccountDto riotDto = new RiotAccountDto("puuid-new", "Spear619", "EUW");
        when(riotAccountService.resolveExactRiotAccount("Spear619", "EUW"))
                .thenReturn(new RiotAccountService.ResolvedRiotAccount(riotDto, 7));
        RiotAccount saved = TestRiotAccounts.riotAccount("puuid-new", "Spear619", "EUW", 7);
        when(riotAccountService.findOrCreate(riotDto, 7)).thenReturn(saved);

        Optional<SummonerSuggestionResponse> result = service.resolve("Spear619", "EUW");

        assertThat(result).isPresent();
        assertThat(result.get().puuid()).isEqualTo("puuid-new");
        assertThat(result.get().riotId()).isEqualTo("Spear619#EUW");
        verify(riotAccountService).findOrCreate(riotDto, 7);
    }

    /** A transient Riot failure (rate limit, timeout) must surface as an error, not a false "not found". */
    @Test
    void resolve_riotRateLimited_propagatesInsteadOfReturningEmpty() {
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("Busy", "EUW"))
                .thenReturn(Optional.empty());
        when(riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase("Busy", "EUW"))
                .thenReturn(Optional.empty());
        when(riotAccountService.resolveExactRiotAccount("Busy", "EUW"))
                .thenThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Riot API rate limit reached"));

        assertThatThrownBy(() -> service.resolve("Busy", "EUW"))
                .isInstanceOf(ResponseStatusException.class)
                .hasFieldOrPropertyWithValue("statusCode", HttpStatus.TOO_MANY_REQUESTS);

        verify(riotAccountService, never()).findOrCreate(any(), any());
    }

    @Test
    void resolve_knownRiotAccount_returnsSuggestion() {
        RiotAccount account = TestRiotAccounts.riotAccount("puuid-1", "Tanor", "EUW", 42);
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("Tanor", "EUW"))
                .thenReturn(Optional.empty());
        when(riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase("Tanor", "EUW"))
                .thenReturn(Optional.of(account));

        Optional<SummonerSuggestionResponse> result = service.resolve("Tanor", "EUW");

        assertThat(result).isPresent();
        assertThat(result.get().puuid()).isEqualTo("puuid-1");
        assertThat(result.get().riotId()).isEqualTo("Tanor#EUW");
        assertThat(result.get().profileIconId()).isEqualTo(42);
    }

    /** A challenge participant isn't necessarily linked to a RiotAccount row — search still finds them. */
    @Test
    void resolve_participantOnlyPlayer_returnsSuggestionWithoutRiotAccountLookup() {
        ChallengeParticipant participant = ChallengeParticipant.create(
                UUID.randomUUID(), new RiotAccountDto("puuid-2", "NoLink", "NA1")
        );
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("NoLink", "NA1"))
                .thenReturn(Optional.of(participant));

        Optional<SummonerSuggestionResponse> result = service.resolve("NoLink", "NA1");

        assertThat(result).isPresent();
        assertThat(result.get().puuid()).isEqualTo("puuid-2");
    }

    /** Viewing a participant-only player should register it into RiotAccount for the leaderboard sync. */
    @Test
    void resolve_participantOnlyPlayer_registersAccount() {
        ChallengeParticipant participant = ChallengeParticipant.create(
                UUID.randomUUID(), new RiotAccountDto("puuid-2", "NoLink", "NA1")
        );
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("NoLink", "NA1"))
                .thenReturn(Optional.of(participant));

        service.resolve("NoLink", "NA1");

        verify(riotAccountService).findOrCreate(
                new RiotAccountDto("puuid-2", "NoLink", "NA1"),
                participant.getProfileIconId()
        );
    }
}
