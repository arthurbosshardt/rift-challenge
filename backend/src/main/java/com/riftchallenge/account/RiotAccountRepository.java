package com.riftchallenge.account;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiotAccountRepository extends JpaRepository<RiotAccount, UUID> {

    Optional<RiotAccount> findByRiotPuuid(String riotPuuid);

    @Query("""
            SELECT r.riotPuuid
            FROM RiotAccount r
            WHERE r.riotPuuid IN :puuids
            """)
    List<String> findPuidsIn(@Param("puuids") Collection<String> puuids);

    @Query("""
            SELECT r
            FROM RiotAccount r
            WHERE LOWER(r.riotGameName) LIKE LOWER(CONCAT(:query, '%'))
               OR LOWER(CONCAT(r.riotGameName, '#', r.riotTagLine)) LIKE LOWER(CONCAT(:query, '%'))
            ORDER BY r.riotGameName ASC
            """)
    List<RiotAccount> searchByRiotId(@Param("query") String query, Pageable pageable);
}
