package com.meetple.backend.global.performance;

import com.meetple.backend.domain.push.fcm.FirebasePushMessageSender;
import com.meetple.backend.domain.push.fcm.PushMessage;
import com.meetple.backend.domain.push.fcm.PushMessageSender;
import com.meetple.backend.domain.push.fcm.PushSendFailure;
import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
@ConditionalOnProperty(
        prefix = "meetple.performance.push-retry",
        name = "enabled",
        havingValue = "true"
)
public class PushRetryMeasurementSender implements PushMessageSender {

    static final String EVENT_TYPE = "PUSH_RETRY_MEASUREMENT";

    private final FirebasePushMessageSender firebaseSender;
    private final AtomicReference<Mode> mode = new AtomicReference<>(Mode.FAIL);
    private final Map<UUID, Integer> attempts = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> successes = new ConcurrentHashMap<>();

    public PushRetryMeasurementSender(FirebasePushMessageSender firebaseSender) {
        this.firebaseSender = firebaseSender;
    }

    @Override
    public PushSendResult send(PushMessage message, List<PushDeviceTarget> targets) {
        if (!EVENT_TYPE.equals(message.data().get("eventType"))) {
            return firebaseSender.send(message, targets);
        }

        UUID eventId = UUID.fromString(message.data().get("eventId"));
        attempts.merge(eventId, 1, Integer::sum);
        if (mode.get() == Mode.FAIL) {
            return new PushSendResult(
                    List.of(),
                    List.of(),
                    targets.stream()
                            .map(target -> new PushSendFailure(target.deviceTokenId(), "UNAVAILABLE"))
                            .toList()
            );
        }

        successes.merge(eventId, 1, Integer::sum);
        return new PushSendResult(
                targets.stream().map(PushDeviceTarget::deviceTokenId).toList(),
                List.of(),
                List.of()
        );
    }

    public void setMode(Mode newMode) {
        mode.set(newMode);
    }

    public Mode mode() {
        return mode.get();
    }

    public int attempts(Iterable<UUID> eventIds) {
        int total = 0;
        for (UUID eventId : eventIds) {
            total += attempts.getOrDefault(eventId, 0);
        }
        return total;
    }

    public int successes(Iterable<UUID> eventIds) {
        int total = 0;
        for (UUID eventId : eventIds) {
            total += Math.min(1, successes.getOrDefault(eventId, 0));
        }
        return total;
    }

    public int duplicateSuccesses(Iterable<UUID> eventIds) {
        int total = 0;
        for (UUID eventId : eventIds) {
            total += Math.max(0, successes.getOrDefault(eventId, 0) - 1);
        }
        return total;
    }

    public enum Mode {
        FAIL,
        SUCCESS
    }
}
