package com.meetple.backend.domain.outbox.repository;

import com.meetple.backend.domain.outbox.entity.OutboxEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
}
