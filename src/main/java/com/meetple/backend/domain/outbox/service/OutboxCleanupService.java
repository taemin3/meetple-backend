package com.meetple.backend.domain.outbox.service;

import com.meetple.backend.domain.outbox.config.OutboxCleanupProperties;
import com.meetple.backend.domain.outbox.repository.OutboxCleanupStore;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "outbox.cleanup", name = "enabled", havingValue = "true")
public class OutboxCleanupService {

    private final OutboxCleanupStore cleanupStore;
    private final OutboxCleanupProperties properties;

    @Scheduled(fixedDelayString = "${outbox.cleanup.interval-ms:3600000}")
    @Transactional
    public int purgeExpiredEvents() {
        return purgeExpiredEvents(Instant.now());
    }

    int purgeExpiredEvents(Instant now) {
        if (!cleanupStore.tryAcquireTransactionLock()) {
            return 0;
        }

        Instant heartbeatCutoff = now.minus(properties.heartbeatMaxAge());
        if (!cleanupStore.isCdcCaughtUp(
                heartbeatCutoff,
                properties.slotName(),
                properties.maxSlotLagBytes()
        )) {
            log.warn("Outbox cleanup skipped because Debezium CDC is not caught up");
            return 0;
        }

        Instant retentionCutoff = now.minus(properties.retention());
        int deleted = cleanupStore.deleteBatchBefore(retentionCutoff, properties.batchSize());
        if (deleted > 0) {
            log.info("Expired outbox events deleted: count={}", deleted);
        }
        return deleted;
    }
}
