package com.cove.server.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {
    @Query("select ps from PushSubscription ps where ps.userId = :userId")
    List<PushSubscription> findByUserId(@Param("userId") UUID userId);

    Optional<PushSubscription> findByEndpoint(String endpoint);
}
