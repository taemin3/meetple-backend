package com.meetple.backend.domain.push.delivery;

import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PushDeliveryService {

    private final PushEventDeliveryRepository pushEventDeliveryRepository;

    @Transactional
    public List<PushDeviceTarget> prepare(UUID eventId, List<PushDeviceTarget> targets) {
        if (targets.isEmpty()) {
            return List.of();
        }

        List<Long> targetIds = targets.stream().map(PushDeviceTarget::deviceTokenId).toList();
        Map<Long, PushEventDelivery> deliveries = pushEventDeliveryRepository
                .findAllByEventIdAndDeviceTokenIdIn(eventId, targetIds)
                .stream()
                .collect(Collectors.toMap(PushEventDelivery::getDeviceTokenId, Function.identity()));

        List<PushDeviceTarget> pendingTargets = targets.stream()
                .filter(target -> prepareTarget(eventId, target.deviceTokenId(), deliveries))
                .toList();
        pushEventDeliveryRepository.saveAll(deliveries.values());
        return pendingTargets;
    }

    @Transactional
    public void record(UUID eventId, PushSendResult result) {
        mark(eventId, result.sentTargetIds(), PushEventDelivery::markSent);
        mark(eventId, result.invalidTargetIds(), PushEventDelivery::markInvalidToken);
        result.failures().forEach(failure -> pushEventDeliveryRepository
                .findByEventIdAndDeviceTokenId(eventId, failure.targetId())
                .ifPresent(delivery -> delivery.markFailed(failure.errorCode())));
    }

    @Transactional
    public void markBatchFailed(UUID eventId, Collection<Long> targetIds, String errorCode) {
        mark(eventId, targetIds, delivery -> delivery.markFailed(errorCode));
    }

    private boolean prepareTarget(
            UUID eventId,
            Long deviceTokenId,
            Map<Long, PushEventDelivery> deliveries
    ) {
        PushEventDelivery delivery = deliveries.computeIfAbsent(
                deviceTokenId,
                id -> PushEventDelivery.create(eventId, id)
        );
        if (delivery.isTerminal()) {
            return false;
        }
        delivery.startAttempt();
        return true;
    }

    private void mark(
            UUID eventId,
            Collection<Long> targetIds,
            java.util.function.Consumer<PushEventDelivery> marker
    ) {
        if (targetIds.isEmpty()) {
            return;
        }
        pushEventDeliveryRepository.findAllByEventIdAndDeviceTokenIdIn(eventId, targetIds)
                .forEach(marker);
    }
}
