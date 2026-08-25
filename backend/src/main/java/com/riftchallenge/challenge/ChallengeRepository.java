package com.riftchallenge.challenge;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeRepository extends JpaRepository<Challenge, UUID> {

    List<Challenge> findByStartAtLessThanEqualOrderByStartAtDesc(Instant startAt);

    List<Challenge> findByOwnerIdOrderByStartAtDesc(UUID ownerId);

    List<Challenge> findByIdInOrderByStartAtDesc(Collection<UUID> ids);

    Optional<Challenge> findByShareSlug(String shareSlug);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Challenge c WHERE c.id = :id")
    Optional<Challenge> findByIdForUpdate(@Param("id") UUID id);
}
