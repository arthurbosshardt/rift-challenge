package com.riftchallenge.synchronization;

import com.riftchallenge.synchronization.RankSnapshot.SnapshotType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankSnapshotRepository extends JpaRepository<RankSnapshot, UUID> {

    Optional<RankSnapshot> findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
            UUID participantId,
            SnapshotType snapshotType
    );

    void deleteByParticipantIdAndSnapshotType(UUID participantId, SnapshotType snapshotType);
}
