package com.meetple.backend.domain.outbox.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.cleanup")
public record OutboxCleanupProperties(
        boolean enabled,
        Duration retention,
        Duration heartbeatMaxAge,
        long maxSlotLagBytes,
        int batchSize,
        String slotName
) {

    public OutboxCleanupProperties {
        if (retention == null || retention.isNegative() || retention.isZero()) {
            throw new IllegalArgumentException("outbox.cleanup.retention must be positive");
        }
        if (heartbeatMaxAge == null || heartbeatMaxAge.isNegative() || heartbeatMaxAge.isZero()) {
            throw new IllegalArgumentException("outbox.cleanup.heartbeat-max-age must be positive");
        }
        if (maxSlotLagBytes < 0) {
            throw new IllegalArgumentException("outbox.cleanup.max-slot-lag-bytes must not be negative");
        }
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("outbox.cleanup.batch-size must be between 1 and 10000");
        }
        if (slotName == null || !slotName.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("outbox.cleanup.slot-name must be a PostgreSQL slot name");
        }
    }
}
