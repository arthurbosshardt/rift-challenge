package com.riftchallenge.leaderboard;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaderboardCacheRepository extends JpaRepository<LeaderboardCache, Short> {
}
