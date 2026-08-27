package com.riftchallenge.leaderboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.riftchallenge.account.AppUser;
import com.riftchallenge.account.AppUserRepository;
import com.riftchallenge.leaderboard.dto.LeaderboardEntryResponse;
import com.riftchallenge.leaderboard.dto.LeaderboardSnapshot;
import com.riftchallenge.leaderboard.dto.LeaderboardWindow;
import com.riftchallenge.match.MatchDetailService;
import com.riftchallenge.match.dto.MatchDetailResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LeaderboardControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-21T10:00:00Z");
    private static final String ADMIN_EMAIL = "tanor.pro@gmail.com";

    @Mock
    private LeaderboardCacheService cacheService;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private AccountMatchRepository accountMatchRepository;

    @Mock
    private MatchDetailService matchDetailService;

    private LeaderboardController controller;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        controller = new LeaderboardController(
                cacheService,
                appUserRepository,
                new LeaderboardProperties(Instant.parse("2026-07-29T12:00:00Z"), ADMIN_EMAIL),
                accountMatchRepository,
                matchDetailService,
                clock
        );
    }

    @Test
    void getPlayerMatchDetail_unknownMatch_throwsNotFound() {
        when(accountMatchRepository.existsByRiotPuuidAndRiotMatchId("puuid-1", "EUW1_1")).thenReturn(false);

        assertThatThrownBy(() -> controller.getPlayerMatchDetail("puuid-1", "EUW1_1"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(404);
    }

    @Test
    void getPlayerMatchDetail_knownMatch_delegatesToMatchDetailService() {
        when(accountMatchRepository.existsByRiotPuuidAndRiotMatchId("puuid-1", "EUW1_1")).thenReturn(true);
        MatchDetailResponse expected = mock(MatchDetailResponse.class);
        when(matchDetailService.buildMatchDetail("EUW1_1", "puuid-1")).thenReturn(expected);

        assertThat(controller.getPlayerMatchDetail("puuid-1", "EUW1_1")).isEqualTo(expected);
    }

    @Test
    void getLeaderboard_anonymous_returnsCanRefreshFalse() {
        when(cacheService.readOrBootstrap()).thenReturn(emptySnapshot());

        var response = controller.getLeaderboard(null);

        assertThat(response.canRefresh()).isFalse();
        assertThat(response.seasonYear()).isEqualTo(2026);
    }

    @Test
    void getLeaderboard_loggedInButNotAdmin_returnsCanRefreshFalse() {
        when(cacheService.readOrBootstrap()).thenReturn(emptySnapshot());
        Authentication authentication = authenticationFor(UUID.randomUUID());
        UUID userId = (UUID) authentication.getPrincipal();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUserWithEmail("someone-else@example.com")));

        var response = controller.getLeaderboard(authentication);

        assertThat(response.canRefresh()).isFalse();
    }

    @Test
    void getLeaderboard_admin_returnsCanRefreshTrue() {
        when(cacheService.readOrBootstrap()).thenReturn(emptySnapshot());
        Authentication authentication = authenticationFor(UUID.randomUUID());
        UUID userId = (UUID) authentication.getPrincipal();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUserWithEmail(ADMIN_EMAIL.toUpperCase())));

        var response = controller.getLeaderboard(authentication);

        assertThat(response.canRefresh()).isTrue();
    }

    @Test
    void refresh_nonAdmin_throwsForbidden() {
        Authentication authentication = authenticationFor(UUID.randomUUID());
        UUID userId = (UUID) authentication.getPrincipal();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUserWithEmail("someone-else@example.com")));

        assertThatThrownBy(() -> controller.refresh(authentication))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode().value())
                .isEqualTo(403);

        verify(cacheService, never()).refresh(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refresh_admin_triggersRefresh() {
        Authentication authentication = authenticationFor(UUID.randomUUID());
        UUID userId = (UUID) authentication.getPrincipal();
        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUserWithEmail(ADMIN_EMAIL)));
        when(cacheService.refresh(NOW)).thenReturn(emptySnapshot());

        var response = controller.refresh(authentication);

        assertThat(response.canRefresh()).isTrue();
        verify(cacheService).refresh(NOW);
    }

    private static Authentication authenticationFor(UUID userId) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userId);
        return authentication;
    }

    private static AppUser appUserWithEmail(String email) {
        AppUser user = AppUser.createFromSupabase(UUID.randomUUID(), email, "someuser");
        return user;
    }

    private static LeaderboardSnapshot emptySnapshot() {
        LeaderboardWindow empty = new LeaderboardWindow(
                List.<LeaderboardEntryResponse>of(),
                List.<LeaderboardEntryResponse>of(),
                List.<LeaderboardEntryResponse>of(),
                List.<LeaderboardEntryResponse>of(),
                20
        );
        return new LeaderboardSnapshot(empty, empty, NOW);
    }
}
