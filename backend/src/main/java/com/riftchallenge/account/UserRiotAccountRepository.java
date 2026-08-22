package com.riftchallenge.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRiotAccountRepository extends JpaRepository<UserRiotAccount, UUID> {

    Optional<UserRiotAccount> findByUserId(UUID userId);

    Optional<UserRiotAccount> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserRiotAccount> findByRiotPuuid(String riotPuuid);

    @Query("""
            SELECT a
            FROM UserRiotAccount a
            WHERE LOWER(a.riotGameName) LIKE LOWER(CONCAT(:query, '%'))
               OR LOWER(CONCAT(a.riotGameName, '#', a.riotTagLine)) LIKE LOWER(CONCAT(:query, '%'))
            ORDER BY a.riotGameName ASC
            """)
    List<UserRiotAccount> searchByRiotId(
            @Param("query") String query,
            org.springframework.data.domain.Pageable pageable
    );

    @Query("""
            SELECT a.riotPuuid
            FROM UserRiotAccount a
            WHERE a.riotPuuid IN :puuids
            """)
    List<String> findLinkedPuidsIn(@Param("puuids") Collection<String> puuids);
}
