package com.riftchallenge.leaderboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riftchallenge.leaderboard.dto.LeaderboardSnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the single cached {@link LeaderboardSnapshot}. Refreshed only by
 * {@link LeaderboardRefreshScheduler} (4x/day, passive) — there is no user-triggered refresh path.
 */
@Service
public class LeaderboardCacheService {

    private static final List<Integer> REFRESH_HOURS_UTC = List.of(0, 6, 12, 18);

    private final LeaderboardCacheRepository cacheRepository;
    private final LeaderboardAccountSyncService accountSyncService;
    private final LeaderboardComputationService computationService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LeaderboardCacheService(
            LeaderboardCacheRepository cacheRepository,
            LeaderboardAccountSyncService accountSyncService,
            LeaderboardComputationService computationService,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.cacheRepository = cacheRepository;
        this.accountSyncService = accountSyncService;
        this.computationService = computationService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /** Not {@code @Transactional}: the bootstrap/stale path below can call {@link #refresh}, which must not run inside an open transaction (see its own note). */
    public LeaderboardSnapshot readOrBootstrap() {
        return cacheRepository.findById(LeaderboardCache.SINGLETON_ID)
                .map(this::deserialize)
                .orElseGet(() -> refresh(clock.instant()));
    }

    /**
     * Not {@code @Transactional}: the account sync makes its own Riot API calls and commits its
     * own writes per account (see {@link LeaderboardAccountSyncService}) — wrapping this in one
     * transaction would hold a DB connection open for the whole sync, which can run for a while.
     */
    public LeaderboardSnapshot refresh(Instant now) {
        accountSyncService.syncAllAccounts(now);
        return recompute(now);
    }

    @Transactional
    LeaderboardSnapshot recompute(Instant now) {
        LeaderboardSnapshot snapshot = computationService.compute(now);
        cacheRepository.save(new LeaderboardCache(serialize(snapshot), now));
        return snapshot;
    }

    static Instant nextRefreshAt(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        for (int hour : REFRESH_HOURS_UTC) {
            Instant candidate = today.atTime(LocalTime.of(hour, 0)).toInstant(ZoneOffset.UTC);
            if (candidate.isAfter(now)) {
                return candidate;
            }
        }
        return today.plusDays(1).atTime(LocalTime.of(REFRESH_HOURS_UTC.get(0), 0)).toInstant(ZoneOffset.UTC);
    }

    private String serialize(LeaderboardSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize leaderboard snapshot", e);
        }
    }

    private LeaderboardSnapshot deserialize(LeaderboardCache cache) {
        try {
            LeaderboardSnapshot snapshot = objectMapper.readValue(cache.getPayload(), LeaderboardSnapshot.class);
            if (isStale(snapshot)) {
                return refresh(clock.instant());
            }
            return snapshot;
        } catch (JsonProcessingException e) {
            // Schema evolved (e.g. new entry fields) — recompute instead of failing the API.
            return refresh(clock.instant());
        }
    }

    /**
     * A cache row written before {@code winRateMinGames} existed deserializes it as Jackson's
     * int default (0) instead of failing — that value is never legitimate, so treat it the same
     * as the schema-evolution case above and recompute.
     */
    private boolean isStale(LeaderboardSnapshot snapshot) {
        return snapshot.season().winRateMinGames() == 0 || snapshot.last7Days().winRateMinGames() == 0;
    }
}
