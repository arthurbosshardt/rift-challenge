package com.riftchallenge.leaderboard;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaderboardAccountRankRepository extends JpaRepository<LeaderboardAccountRank, String> {

    Optional<LeaderboardAccountRank> findByRiotPuuid(String riotPuuid);
}
