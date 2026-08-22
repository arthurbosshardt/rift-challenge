package com.riftchallenge.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRiotAccountRepository extends JpaRepository<UserRiotAccount, UUID> {

    @Query("""
            SELECT l
            FROM UserRiotAccount l
            JOIN FETCH l.riotAccount
            WHERE l.userId = :userId
            """)
    Optional<UserRiotAccount> findByUserId(@Param("userId") UUID userId);

    @Query("""
            SELECT l
            FROM UserRiotAccount l
            JOIN FETCH l.riotAccount
            WHERE l.id = :id AND l.userId = :userId
            """)
    Optional<UserRiotAccount> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("""
            SELECT l
            FROM UserRiotAccount l
            JOIN FETCH l.riotAccount
            WHERE l.riotAccount.riotPuuid = :riotPuuid
            """)
    Optional<UserRiotAccount> findByRiotAccountPuuid(@Param("riotPuuid") String riotPuuid);

    @Query("""
            SELECT CASE WHEN COUNT(l) > 0 THEN true ELSE false END
            FROM UserRiotAccount l
            WHERE l.riotAccount.id = :riotAccountId
            """)
    boolean existsByRiotAccountId(@Param("riotAccountId") UUID riotAccountId);
}
