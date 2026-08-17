package com.riftrace.race;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaceRepository extends JpaRepository<Race, UUID> {

    List<Race> findByIsPublicTrueAndStartAtLessThanEqualOrderByStartAtDesc(Instant startAt);

    List<Race> findByOwnerIdOrderByStartAtDesc(UUID ownerId);

    Optional<Race> findByShareSlug(UUID shareSlug);
}
