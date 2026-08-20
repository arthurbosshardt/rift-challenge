package com.riftchallenge.synchronization;

import com.riftchallenge.synchronization.RankSnapshot.SnapshotType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankSnapshotRepository extends JpaRepository<RankSnapshot, UUID> {

    Optional<RankSnapshot> findFirstByParticipantIdAndSnapshotTypeOrderByCapturedAtDesc(
            UUID participantId,
            SnapshotType snapshotType
    );

    List<RankSnapshot> findByParticipantIdInAndSnapshotType(
            List<UUID> participantIds,
            SnapshotType snapshotType
    );

    void deleteByParticipantIdAndSnapshotType(UUID participantId, SnapshotType snapshotType);
}
