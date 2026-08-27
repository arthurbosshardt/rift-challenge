package com.riftchallenge.leaderboard;

import com.riftchallenge.account.RiotAccount;
import com.riftchallenge.account.RiotAccountRepository;
import com.riftchallenge.riot.RiotMatchLookupService;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One-off "rattrapage" backfill: fetches the full ranked-solo match history for every account
 * already in the system, one parallel task per account on {@code riotSyncExecutor}. Unlike the
 * regular per-request sync, this isn't budget-capped — it's meant to be run once a production
 * Riot key with real headroom is in place, relying on {@link
 * com.riftchallenge.riot.RiotAppRateLimiter} to keep the actual request rate within Riot's quota.
 */
@Service
public class RiotHistoryBackfillService {

    private static final Logger log = LoggerFactory.getLogger(RiotHistoryBackfillService.class);

    private final RiotAccountRepository riotAccountRepository;
    private final LeaderboardAccountSyncService leaderboardAccountSyncService;
    private final RiotMatchLookupService riotMatchLookupService;
    private final ExecutorService riotSyncExecutor;

    public RiotHistoryBackfillService(
            RiotAccountRepository riotAccountRepository,
            LeaderboardAccountSyncService leaderboardAccountSyncService,
            RiotMatchLookupService riotMatchLookupService,
            ExecutorService riotSyncExecutor
    ) {
        this.riotAccountRepository = riotAccountRepository;
        this.leaderboardAccountSyncService = leaderboardAccountSyncService;
        this.riotMatchLookupService = riotMatchLookupService;
        this.riotSyncExecutor = riotSyncExecutor;
    }

    public void backfillAllAccounts() {
        List<RiotAccount> accounts = riotAccountRepository.findAll();
        log.info("Starting full-history backfill for {} accounts", accounts.size());

        List<Future<?>> futures = accounts.stream()
                .<Future<?>>map(account -> riotSyncExecutor.submit(() -> backfillOne(account)))
                .toList();

        int succeeded = 0;
        int failed = 0;
        for (Future<?> future : futures) {
            try {
                future.get();
                succeeded++;
            } catch (ExecutionException exception) {
                failed++;
                log.warn("Backfill task failed", exception.getCause());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn("Backfill interrupted while waiting for account tasks to finish");
                break;
            }
        }

        log.info("Full-history backfill finished: {} accounts succeeded, {} failed", succeeded, failed);
    }

    private void backfillOne(RiotAccount account) {
        riotMatchLookupService.beginRefreshScope();
        try {
            int imported = leaderboardAccountSyncService.backfillFullHistory(account);
            log.info("Backfilled account {}: {} new matches imported", account.getId(), imported);
        } catch (RuntimeException exception) {
            log.warn("Backfill failed for account {}", account.getId(), exception);
        } finally {
            riotMatchLookupService.endRefreshScope();
        }
    }
}
