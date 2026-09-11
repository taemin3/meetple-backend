package com.meetple.backend.domain.outbox.repository;

import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OutboxCleanupStore {

    private static final int ADVISORY_LOCK_NAMESPACE = 2_026_0911;
    private static final int ADVISORY_LOCK_KEY = 1;

    private final JdbcTemplate jdbcTemplate;

    public boolean tryAcquireTransactionLock() {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_xact_lock(?, ?)",
                Boolean.class,
                ADVISORY_LOCK_NAMESPACE,
                ADVISORY_LOCK_KEY
        );
        return Boolean.TRUE.equals(acquired);
    }

    public boolean isCdcCaughtUp(
            Instant heartbeatCutoff,
            String slotName,
            long maxSlotLagBytes
    ) {
        Boolean healthy = jdbcTemplate.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM public.debezium_heartbeat h
                    JOIN pg_catalog.pg_replication_slots s ON s.slot_name = ?
                    WHERE h.id = 1
                      AND h.updated_at >= ?
                      AND s.active
                      AND s.wal_status = 'reserved'
                      AND s.confirmed_flush_lsn IS NOT NULL
                      AND pg_wal_lsn_diff(pg_current_wal_lsn(), s.confirmed_flush_lsn) <= ?
                )
                """,
                Boolean.class,
                slotName,
                Timestamp.from(heartbeatCutoff),
                maxSlotLagBytes
        );
        return Boolean.TRUE.equals(healthy);
    }

    public int deleteBatchBefore(Instant cutoff, int batchSize) {
        return jdbcTemplate.update(
                """
                WITH candidates AS (
                    SELECT id
                    FROM public.outbox_events
                    WHERE occurred_at < ?
                    ORDER BY occurred_at
                    LIMIT ?
                    FOR UPDATE SKIP LOCKED
                )
                DELETE FROM public.outbox_events o
                USING candidates c
                WHERE o.id = c.id
                """,
                Timestamp.from(cutoff),
                batchSize
        );
    }
}
