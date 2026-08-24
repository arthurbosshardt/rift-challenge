package com.riftchallenge.summoner;

import static org.assertj.core.api.Assertions.assertThat;
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
    void resolve_unknownRiotId_returnsEmpty() {
        when(participantRepository.findFirstByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCaseOrderByCreatedAtDesc("Ghost", "EUW"))
                .thenReturn(Optional.empty());
        when(riotAccountRepository.findByRiotGameNameIgnoreCaseAndRiotTagLineIgnoreCase("Ghost", "EUW"))
                .thenReturn(Optional.empty());

        assertThat(service.resolve("Ghost", "EUW")).isEmpty();
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
