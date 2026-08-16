package com.riftrace.synchronization;

import com.riftrace.synchronization.RankSnapshot.SnapshotType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankSnapshotRepository extends JpaRepository<RankSnapshot, UUID> {

    Optional<RankSnapshot> findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
            UUID participantId,
            SnapshotType snapshotType
    );
}
