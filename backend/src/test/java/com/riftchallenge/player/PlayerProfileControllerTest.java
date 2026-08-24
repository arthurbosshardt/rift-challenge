package com.riftchallenge.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.riftchallenge.challenge.ChallengeService;
import com.riftchallenge.challenge.RecentActivityService;
import com.riftchallenge.challenge.dto.AccountRecentGamesResponse;
import com.riftchallenge.challenge.dto.ChallengeListResponse;
import com.riftchallenge.summoner.PlayerLookupService;
import com.riftchallenge.summoner.SummonerSuggestionResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PlayerProfileControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-24T10:00:00Z");

    @Mock
    private PlayerLookupService playerLookupService;

    @Mock
    private RecentActivityService recentActivityService;

    @Mock
    private ChallengeService challengeService;

    @Mock
    private HttpServletRequest request;

    private PlayerProfileController controller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        controller = new PlayerProfileController(
                playerLookupService,
                recentActivityService,
                challengeService,
                new PlayerProfileRequestThrottle(clock)
        );
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void resolve_unknownRiotId_throwsNotFound() {
        when(playerLookupService.resolve("Ghost", "EUW")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.resolve(request, null, "Ghost#EUW"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void resolve_knownRiotId_returnsSuggestion() {
        SummonerSuggestionResponse suggestion = new SummonerSuggestionResponse("puuid-1", "Tanor", "EUW", "Tanor#EUW", null);
        when(playerLookupService.resolve("Tanor", "EUW")).thenReturn(Optional.of(suggestion));

        assertThat(controller.resolve(request, null, "Tanor#EUW")).isEqualTo(suggestion);
    }

    /** A stored gameName can contain a single internal space — must not be stripped like user-typed input. */
    @Test
    void resolve_gameNameWithInternalSpace_isPreserved() {
        SummonerSuggestionResponse suggestion =
                new SummonerSuggestionResponse("puuid-1", "twtv Peng04", "NMIXX", "twtv Peng04#NMIXX", null);
        when(playerLookupService.resolve("twtv Peng04", "NMIXX")).thenReturn(Optional.of(suggestion));

        assertThat(controller.resolve(request, null, "twtv Peng04#NMIXX")).isEqualTo(suggestion);
    }

    @Test
    void getActivity_unknownRiotId_throwsNotFound() {
        when(recentActivityService.getActivityForRiotId("Ghost", "EUW")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getActivity(request, null, "Ghost#EUW"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void getActivity_knownRiotId_returnsActivity() {
        AccountRecentGamesResponse activity = mock(AccountRecentGamesResponse.class);
        when(recentActivityService.getActivityForRiotId("Tanor", "EUW")).thenReturn(Optional.of(activity));

        assertThat(controller.getActivity(request, null, "Tanor#EUW")).isEqualTo(activity);
    }

    @Test
    void getChallenges_unknownRiotId_throwsNotFound() {
        when(playerLookupService.resolve("Ghost", "EUW")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getChallenges(request, null, "Ghost#EUW"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void getChallenges_knownRiotId_delegatesWithResolvedPuuid() {
        SummonerSuggestionResponse suggestion = new SummonerSuggestionResponse("puuid-1", "Tanor", "EUW", "Tanor#EUW", null);
        when(playerLookupService.resolve("Tanor", "EUW")).thenReturn(Optional.of(suggestion));
        ChallengeListResponse response = mock(ChallengeListResponse.class);
        when(challengeService.listChallengesForPuuids(List.of("puuid-1"))).thenReturn(response);

        assertThat(controller.getChallenges(request, null, "Tanor#EUW")).isEqualTo(response);
    }
}
