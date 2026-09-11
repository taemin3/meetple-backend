package com.meetple.backend.domain.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.outbox.config.OutboxCleanupProperties;
import com.meetple.backend.domain.outbox.repository.OutboxCleanupStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T00:00:00Z");
    private static final long MAX_SLOT_LAG_BYTES = 64L * 1024 * 1024;

    @Mock
    private OutboxCleanupStore cleanupStore;

    private OutboxCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        cleanupService = new OutboxCleanupService(
                cleanupStore,
                new OutboxCleanupProperties(
                        true,
                        Duration.ofDays(7),
                        Duration.ofMinutes(3),
                        MAX_SLOT_LAG_BYTES,
                        1000,
                        "meetple_outbox"
                )
        );
    }

    @Test
    void skipsCleanupWhenAnotherInstanceOwnsTheLock() {
        given(cleanupStore.tryAcquireTransactionLock()).willReturn(false);

        int deleted = cleanupService.purgeExpiredEvents(NOW);

        assertThat(deleted).isZero();
        verify(cleanupStore, never()).isCdcCaughtUp(
                NOW.minus(Duration.ofMinutes(3)),
                "meetple_outbox",
                MAX_SLOT_LAG_BYTES
        );
        verify(cleanupStore, never()).deleteBatchBefore(NOW.minus(Duration.ofDays(7)), 1000);
    }

    @Test
    void skipsCleanupWhenCdcIsNotCaughtUp() {
        given(cleanupStore.tryAcquireTransactionLock()).willReturn(true);
        given(cleanupStore.isCdcCaughtUp(
                NOW.minus(Duration.ofMinutes(3)),
                "meetple_outbox",
                MAX_SLOT_LAG_BYTES
        )).willReturn(false);

        int deleted = cleanupService.purgeExpiredEvents(NOW);

        assertThat(deleted).isZero();
        verify(cleanupStore, never()).deleteBatchBefore(NOW.minus(Duration.ofDays(7)), 1000);
    }

    @Test
    void deletesOneBoundedBatchWhenCdcIsCaughtUp() {
        given(cleanupStore.tryAcquireTransactionLock()).willReturn(true);
        given(cleanupStore.isCdcCaughtUp(
                NOW.minus(Duration.ofMinutes(3)),
                "meetple_outbox",
                MAX_SLOT_LAG_BYTES
        )).willReturn(true);
        given(cleanupStore.deleteBatchBefore(NOW.minus(Duration.ofDays(7)), 1000)).willReturn(1000);

        int deleted = cleanupService.purgeExpiredEvents(NOW);

        assertThat(deleted).isEqualTo(1000);
        verify(cleanupStore).deleteBatchBefore(NOW.minus(Duration.ofDays(7)), 1000);
    }
}
