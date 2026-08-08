package com.meetple.backend.domain.push.delivery;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushEventDeliveryRepository extends JpaRepository<PushEventDelivery, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select delivery
            from PushEventDelivery delivery
            where delivery.eventId = :eventId
              and delivery.deviceTokenId in :deviceTokenIds
            """)
    List<PushEventDelivery> findAllForUpdate(
            @Param("eventId") UUID eventId,
            @Param("deviceTokenIds") Collection<Long> deviceTokenIds
    );
}
