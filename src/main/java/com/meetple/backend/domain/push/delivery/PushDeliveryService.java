package com.meetple.backend.domain.push.delivery;

import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class PushDeliveryService {

    private static final int MAX_CLAIM_ATTEMPTS = 3;
    private static final Duration CLAIM_LEASE_DURATION = Duration.ofMinutes(5);

    private final PushEventDeliveryRepository pushEventDeliveryRepository;
    private final TransactionTemplate transactionTemplate;

    public PushDeliveryClaim prepare(UUID eventId, List<PushDeviceTarget> targets) {
        if (targets.isEmpty()) {
            return PushDeliveryClaim.empty();
        }

        DataIntegrityViolationException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_CLAIM_ATTEMPTS; attempt++) {
            try {
                PushDeliveryClaim claim = transactionTemplate.execute(status ->
                        prepareInTransaction(eventId, targets)
                );
                return Objects.requireNonNull(claim);
            } catch (DataIntegrityViolationException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    private PushDeliveryClaim prepareInTransaction(
            UUID eventId,
            List<PushDeviceTarget> targets
    ) {
        List<Long> targetIds = targets.stream().map(PushDeviceTarget::deviceTokenId).toList();
        Map<Long, PushEventDelivery> deliveries = pushEventDeliveryRepository
                .findAllForUpdate(eventId, targetIds)
                .stream()
                .collect(Collectors.toMap(PushEventDelivery::getDeviceTokenId, Function.identity()));

        LocalDateTime now = LocalDateTime.now();
        boolean hasActiveClaim = deliveries.values().stream()
                .anyMatch(delivery -> !delivery.isTerminal() && delivery.hasActiveClaim(now));
        if (hasActiveClaim) {
            return PushDeliveryClaim.blocked();
        }

        UUID claimId = UUID.randomUUID();
        LocalDateTime claimedUntil = now.plus(CLAIM_LEASE_DURATION);
        List<PushDeviceTarget> claimedTargets = targets.stream()
                .filter(target -> claimTarget(
                        eventId,
                        target.deviceTokenId(),
                        claimId,
                        now,
                        claimedUntil,
                        deliveries
                ))
                .toList();
        pushEventDeliveryRepository.saveAllAndFlush(deliveries.values());
        if (claimedTargets.isEmpty()) {
            return PushDeliveryClaim.empty();
        }
        return new PushDeliveryClaim(claimId, claimedTargets, false);
    }

    @Transactional
    public void record(UUID eventId, UUID claimId, PushSendResult result) {
        Set<Long> sentTargetIds = Set.copyOf(result.sentTargetIds());
        Set<Long> invalidTargetIds = Set.copyOf(result.invalidTargetIds());
        Map<Long, String> failureCodes = result.failures().stream()
                .collect(Collectors.toMap(
                        failure -> failure.targetId(),
                        failure -> failure.errorCode(),
                        (first, second) -> second
                ));

        Set<Long> resultTargetIds = new LinkedHashSet<>();
        resultTargetIds.addAll(sentTargetIds);
        resultTargetIds.addAll(invalidTargetIds);
        resultTargetIds.addAll(failureCodes.keySet());
        if (resultTargetIds.isEmpty()) {
            return;
        }

        pushEventDeliveryRepository.findAllForUpdate(eventId, resultTargetIds)
                .forEach(delivery -> markResult(
                        delivery,
                        claimId,
                        sentTargetIds,
                        invalidTargetIds,
                        failureCodes
                ));
    }

    private boolean claimTarget(
            UUID eventId,
            Long deviceTokenId,
            UUID claimId,
            LocalDateTime now,
            LocalDateTime claimedUntil,
            Map<Long, PushEventDelivery> deliveries
    ) {
        PushEventDelivery delivery = deliveries.computeIfAbsent(
                deviceTokenId,
                id -> PushEventDelivery.create(eventId, id)
        );
        return delivery.claim(claimId, now, claimedUntil);
    }

    private void markResult(
            PushEventDelivery delivery,
            UUID claimId,
            Set<Long> sentTargetIds,
            Set<Long> invalidTargetIds,
            Map<Long, String> failureCodes
    ) {
        if (!delivery.isClaimedBy(claimId)) {
            return;
        }
        Long deviceTokenId = delivery.getDeviceTokenId();
        if (sentTargetIds.contains(deviceTokenId)) {
            delivery.markSent();
        } else if (invalidTargetIds.contains(deviceTokenId)) {
            delivery.markInvalidToken();
        } else if (failureCodes.containsKey(deviceTokenId)) {
            delivery.markFailed(failureCodes.get(deviceTokenId));
        }
    }
}
