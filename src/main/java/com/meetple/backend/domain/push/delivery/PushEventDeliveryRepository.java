package com.meetple.backend.domain.push.delivery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushEventDeliveryRepository extends JpaRepository<PushEventDelivery, Long> {

    List<PushEventDelivery> findAllByEventIdAndDeviceTokenIdIn(
            UUID eventId,
            Collection<Long> deviceTokenIds
    );

    Optional<PushEventDelivery> findByEventIdAndDeviceTokenId(UUID eventId, Long deviceTokenId);
}
