package com.riftchallenge.challenge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.riftchallenge.TestRiotAccounts;
import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountMatchRepository;
import com.riftchallenge.leaderboard.LeaderboardAccountSyncService;
import com.riftchallenge.leaderboard.LeaderboardProperties;
import com.riftchallenge.riot.RiotMatchLookupService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityAccountBackgroundSyncServiceTest {

    private static final Instant SEASON_START = Instant.parse("2026-01-08T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-08-22T09:00:00Z");
    private static final String PUUID = "puuid-1";

    @Mock
    private LeaderboardAccountSyncService accountSyncService;

    @Mock
    private LeaderboardAccountMatchRepository accountMatchRepository;

    @Mock
    private RiotAccountRepository riotAccountRepository;

    @Mock
    private RiotMatchLookupService riotMatchLookupService;

    private ExecutorService executorService;
    private ActivityAccountBackgroundSyncService service;

    @BeforeEach
    void setUp() {
        executorService = Executors.newSingleThreadExecutor();
        service = new ActivityAccountBackgroundSyncService(
                accountSyncService,
                accountMatchRepository,
                riotAccountRepository,
                riotMatchLookupService,
                new LeaderboardProperties(SEASON_START, "admin@example.com"),
                executorService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void scheduleSyncIfIdle_runsSyncOffRequestThread() {
        RiotAccount account = riotAccount();
        when(accountMatchRepository.countSeasonMatchesSince(PUUID, SEASON_START)).thenReturn(10L, 10L);
        when(accountSyncService.syncAccountForActivity(account, 120))
                .thenReturn(new LeaderboardAccountSyncService.ActivitySyncBatchResult(5, false));

        service.scheduleSyncIfIdle(account, 120, 10);

        verify(riotMatchLookupService, timeout(2_000)).beginRefreshScope();
        verify(accountSyncService, timeout(2_000)).syncAccountForActivity(account, 120);
        verify(riotMatchLookupService, timeout(2_000)).endRefreshScope();
    }

    @Test
    void scheduleSyncIfIdle_skipsWhenSeasonAlreadyStored() {
        RiotAccount account = riotAccount();

        service.scheduleSyncIfIdle(account, 120, 120);

        verifyNoInteractions(accountSyncService, riotMatchLookupService);
    }

    /** Rows imported before V27 added combat-stat columns are stuck at null KDA/CS forever unless
     *  this runs even though the season's match count is already fully caught up. */
    @Test
    void scheduleSyncIfIdle_backfillsCombatStatsWhenSeasonAlreadyStored() {
        RiotAccount account = riotAccount();
        when(accountMatchRepository.findMatchIdsMissingCombatStatsSince(eq(PUUID), eq(SEASON_START), any()))
                .thenReturn(List.of("match-1"));

        service.scheduleSyncIfIdle(account, 120, 120);

        verify(accountSyncService, timeout(2_000)).backfillMissingCombatStats(PUUID, 15);
    }

    @Test
    void startCatchUpChain_skipsWhenAlreadyComplete() {
        RiotAccount account = riotAccount();
        when(accountMatchRepository.countSeasonMatchesSince(PUUID, SEASON_START)).thenReturn(137L);

        service.startCatchUpChain(account, 137, 0);

        verifyNoInteractions(accountSyncService);
    }

    @Test
    void scheduleSyncIfIdle_skipsWhenCatchUpAlreadyActive() {
        RiotAccount account = riotAccount();
        when(accountMatchRepository.countSeasonMatchesSince(PUUID, SEASON_START)).thenReturn(10L, 10L);
        when(accountSyncService.syncAccountForActivity(account, 120))
                .thenReturn(new LeaderboardAccountSyncService.ActivitySyncBatchResult(5, false));

        service.startCatchUpChain(account, 120, 0);
        service.scheduleSyncIfIdle(account, 120, 10);

        verify(accountSyncService, timeout(2_000).times(1)).syncAccountForActivity(account, 120);
    }

    @Test
    void scheduleSyncIfIdle_skipsWhenSeasonHistoryExhausted() {
        RiotAccount account = riotAccount();
        when(accountMatchRepository.countSeasonMatchesSince(PUUID, SEASON_START)).thenReturn(125L);
        when(accountSyncService.syncAccountForActivity(account, 137))
                .thenReturn(new LeaderboardAccountSyncService.ActivitySyncBatchResult(0, true));

        service.startCatchUpChain(account, 137, 0);
        verify(accountSyncService, timeout(2_000)).syncAccountForActivity(account, 137);

        service.scheduleSyncIfIdle(account, 137, 125);

        verify(accountSyncService, timeout(2_000).times(1)).syncAccountForActivity(account, 137);
    }

    @Test
    void runCatchUpBatch_marksSeasonHistoryExhaustedWhenAllMatchIdsImported() {
        RiotAccount account = riotAccount();
        when(accountMatchRepository.countSeasonMatchesSince(PUUID, SEASON_START)).thenReturn(125L, 125L);
        when(accountSyncService.syncAccountForActivity(account, 137))
                .thenReturn(new LeaderboardAccountSyncService.ActivitySyncBatchResult(5, true));
        when(riotAccountRepository.save(account)).thenReturn(account);

        service.startCatchUpChain(account, 137, 0);

        verify(accountSyncService, timeout(2_000)).syncAccountForActivity(account, 137);
        assertThat(service.isSeasonHistoryExhausted(account.getId())).isTrue();
        assertThat(service.isCatchUpActive(account.getId())).isFalse();
        verify(riotAccountRepository, timeout(2_000)).save(account);
        assertThat(account.isActivitySeasonHistoryExhausted()).isTrue();
    }

    private static RiotAccount riotAccount() {
        return TestRiotAccounts.riotAccount(PUUID, "Player", "EUW");
    }
}
