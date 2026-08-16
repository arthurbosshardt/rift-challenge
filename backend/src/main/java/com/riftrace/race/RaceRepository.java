package com.riftrace.race;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RaceRepository extends JpaRepository<Race, UUID> {

    List<Race> findByIsPublicTrueOrderByStartAtDesc();

    List<Race> findByOwnerIdOrderByStartAtDesc(UUID ownerId);

    Optional<Race> findByShareSlug(UUID shareSlug);
}
