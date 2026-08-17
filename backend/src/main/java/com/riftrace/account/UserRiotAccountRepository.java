package com.riftrace.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRiotAccountRepository extends JpaRepository<UserRiotAccount, UUID> {

    List<UserRiotAccount> findByUserIdOrderByCreatedAtAsc(UUID userId);

    long countByUserId(UUID userId);

    Optional<UserRiotAccount> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserRiotAccount> findByRiotPuuid(String riotPuuid);

    boolean existsByUserIdAndRiotPuuid(UUID userId, String riotPuuid);
}
