package com.riftchallenge.challenge;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    List<Challenge> findByStartAtLessThanEqualOrderByStartAtDesc(Instant startAt);

    List<Challenge> findByOwnerIdOrderByStartAtDesc(UUID ownerId);

    List<Challenge> findByIdInOrderByStartAtDesc(Collection<UUID> ids);

    Optional<Challenge> findByShareSlug(String shareSlug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
