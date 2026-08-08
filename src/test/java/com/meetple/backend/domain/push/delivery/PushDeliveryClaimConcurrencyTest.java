package com.meetple.backend.domain.push.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class PushDeliveryClaimConcurrencyTest {

    @Autowired
    private PushDeliveryService pushDeliveryService;

    @Autowired
    private PushEventDeliveryRepository pushEventDeliveryRepository;

    @BeforeEach
    void clearDeliveries() {
        pushEventDeliveryRepository.deleteAll();
    }

    @Test
    void grantsSameEventTargetToOnlyOneConcurrentConsumer() throws Exception {
        UUID eventId = UUID.randomUUID();
        List<PushDeviceTarget> targets = List.of(new PushDeviceTarget(10L, "token-10"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            Future<PushDeliveryClaim> first = executor.submit(() -> claimAfterSignal(
                    eventId,
                    targets,
                    ready,
                    start
            ));
            Future<PushDeliveryClaim> second = executor.submit(() -> claimAfterSignal(
                    eventId,
                    targets,
                    ready,
                    start
            ));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

            start.countDown();
            List<PushDeliveryClaim> claims = List.of(
                    first.get(10, TimeUnit.SECONDS),
                    second.get(10, TimeUnit.SECONDS)
            );

            assertThat(claims).filteredOn(claim -> !claim.targets().isEmpty()).hasSize(1);
            assertThat(claims).filteredOn(PushDeliveryClaim::blockedByActiveClaim).hasSize(1);
            assertThat(pushEventDeliveryRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private PushDeliveryClaim claimAfterSignal(
            UUID eventId,
            List<PushDeviceTarget> targets,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent claim start signal timed out.");
        }
        return pushDeliveryService.prepare(eventId, targets);
    }
}
