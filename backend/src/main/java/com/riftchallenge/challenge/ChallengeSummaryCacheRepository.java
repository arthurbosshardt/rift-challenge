package com.riftchallenge.challenge;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeSummaryCacheRepository extends JpaRepository<ChallengeSummaryCache, UUID> {

    List<ChallengeSummaryCache> findByChallengeIdInOrderByStartAtDesc(Collection<UUID> challengeIds);

    List<ChallengeSummaryCache> findAllByOrderByStartAtDesc();
}
