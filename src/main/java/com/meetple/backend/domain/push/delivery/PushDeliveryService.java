package com.meetple.backend.domain.push.delivery;

import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

        pushEventDeliveryRepository.findAllByEventIdAndDeviceTokenIdIn(eventId, resultTargetIds)
                .forEach(delivery -> markResult(
                        delivery,
                        sentTargetIds,
                        invalidTargetIds,
                        failureCodes
                ));
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

    private void markResult(
            PushEventDelivery delivery,
            Set<Long> sentTargetIds,
            Set<Long> invalidTargetIds,
            Map<Long, String> failureCodes
    ) {
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
