package com.meetple.backend.domain.push.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.push.delivery.PushDeliveryService;
import com.meetple.backend.domain.push.event.PushEventEnvelope;
import com.meetple.backend.domain.push.event.PushEventTopic;
import com.meetple.backend.domain.push.fcm.PushMessage;
import com.meetple.backend.domain.push.fcm.PushMessageSender;
import com.meetple.backend.domain.push.fcm.PushSendException;
import com.meetple.backend.domain.push.fcm.PushSendResult;
import com.meetple.backend.domain.push.service.PushDeviceTarget;
import com.meetple.backend.domain.push.service.PushDeviceTokenService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "push.kafka", name = "consumer-enabled", havingValue = "true")
public class PushEventProcessor {

    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final PushDeviceTokenService pushDeviceTokenService;
    private final PushDeliveryService pushDeliveryService;
    private final PushMessageSender pushMessageSender;

    public void process(String topic, String payload) {
        PushEventEnvelope envelope = parseEnvelope(payload);
        validateEnvelope(envelope);
        DispatchPlan plan = createDispatchPlan(topic, envelope);

        List<PushDeviceTarget> targets = pushDeviceTokenService.findTargets(plan.recipientMemberIds());
        List<PushDeviceTarget> pendingTargets = pushDeliveryService.prepare(envelope.eventId(), targets);
        if (pendingTargets.isEmpty()) {
            return;
        }

        PushSendResult result;
        try {
            result = pushMessageSender.send(plan.message(), pendingTargets);
        } catch (PushSendException exception) {
            pushDeliveryService.record(envelope.eventId(), exception.getPartialResult());
            pushDeviceTokenService.removeInvalidTargets(
                    exception.getPartialResult().invalidTargetIds()
            );
            throw exception;
        }

        pushDeliveryService.record(envelope.eventId(), result);
        pushDeviceTokenService.removeInvalidTargets(result.invalidTargetIds());
        if (result.hasFailures()) {
            throw new PushEventProcessingException(
                    "FCM delivery failed for " + result.failures().size() + " target(s)."
            );
        }
    }

    private PushEventEnvelope parseEnvelope(String payload) {
        try {
            return objectMapper.readValue(payload, PushEventEnvelope.class);
        } catch (JsonProcessingException exception) {
            throw new PushEventProcessingException("Invalid push event JSON.", exception);
        }
    }

    private void validateEnvelope(PushEventEnvelope envelope) {
        if (envelope.eventId() == null || !StringUtils.hasText(envelope.eventType())) {
            throw new PushEventProcessingException("Push event metadata is missing.");
        }
        if (envelope.schemaVersion() != SUPPORTED_SCHEMA_VERSION) {
            throw new PushEventProcessingException(
                    "Unsupported push event schema version: " + envelope.schemaVersion()
            );
        }
        if (envelope.data() == null || !envelope.data().isObject()) {
            throw new PushEventProcessingException("Push event data must be a JSON object.");
        }
    }

    private DispatchPlan createDispatchPlan(String topic, PushEventEnvelope envelope) {
        if (PushEventTopic.NOTIFICATION.getValue().equals(topic)) {
            return generalNotificationPlan(envelope);
        }
        if (PushEventTopic.CHAT.getValue().equals(topic)) {
            return chatNotificationPlan(envelope);
        }
        throw new PushEventProcessingException("Unsupported push topic: " + topic);
    }

    private DispatchPlan generalNotificationPlan(PushEventEnvelope envelope) {
        JsonNode data = envelope.data();
        Long recipientMemberId = requiredLong(data, "recipientMemberId");
        String title = requiredText(data, "title");
        String body = requiredBody(data);

        Map<String, String> messageData = baseMessageData(envelope, "MEETING_DETAIL");
        copyScalar(data, messageData, "notificationId");
        copyScalar(data, messageData, "meetingId");

        return new DispatchPlan(
                List.of(recipientMemberId),
                new PushMessage(title, body, messageData, null, null)
        );
    }

    private DispatchPlan chatNotificationPlan(PushEventEnvelope envelope) {
        JsonNode data = envelope.data();
        Set<Long> recipientMemberIds = requiredLongSet(data, "recipientMemberIds");
        Long senderMemberId = optionalLong(data, "senderMemberId");
        if (senderMemberId != null) {
            recipientMemberIds.remove(senderMemberId);
        }

        String roomId = requiredScalarText(data, "roomId");
        String title = requiredText(data, "title");
        String body = requiredBody(data);
        Map<String, String> messageData = baseMessageData(envelope, "CHAT_ROOM");
        copyScalar(data, messageData, "roomId");
        copyScalar(data, messageData, "chatMessageId");
        copyScalar(data, messageData, "roomSequence");
        copyScalar(data, messageData, "senderMemberId");
        copyScalar(data, messageData, "senderNickname");

        String groupKey = "chat-room-" + roomId;
        return new DispatchPlan(
                List.copyOf(recipientMemberIds),
                new PushMessage(title, body, messageData, groupKey, groupKey)
        );
    }

    private Map<String, String> baseMessageData(PushEventEnvelope envelope, String route) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("eventId", envelope.eventId().toString());
        data.put("eventType", envelope.eventType());
        data.put("schemaVersion", Integer.toString(envelope.schemaVersion()));
        data.put("route", route);
        putIfPresent(data, "aggregateType", envelope.aggregateType());
        putIfPresent(data, "aggregateId", envelope.aggregateId());
        return data;
    }

    private String requiredBody(JsonNode data) {
        JsonNode body = data.get("body");
        if (body == null || !body.isTextual() || !StringUtils.hasText(body.textValue())) {
            return requiredText(data, "message");
        }
        return body.textValue();
    }

    private String requiredText(JsonNode data, String fieldName) {
        JsonNode field = data.get(fieldName);
        if (field == null || !field.isTextual() || !StringUtils.hasText(field.textValue())) {
            throw new PushEventProcessingException(fieldName + " must be a non-blank string.");
        }
        return field.textValue();
    }

    private String requiredScalarText(JsonNode data, String fieldName) {
        JsonNode field = data.get(fieldName);
        if (field == null || field.isContainerNode() || !StringUtils.hasText(field.asText())) {
            throw new PushEventProcessingException(fieldName + " must be a scalar value.");
        }
        return field.asText();
    }

    private Long requiredLong(JsonNode data, String fieldName) {
        Long value = optionalLong(data, fieldName);
        if (value == null) {
            throw new PushEventProcessingException(fieldName + " must be an integer.");
        }
        return value;
    }

    private Long optionalLong(JsonNode data, String fieldName) {
        JsonNode field = data.get(fieldName);
        if (field == null || field.isNull() || !field.canConvertToLong()) {
            return null;
        }
        return field.longValue();
    }

    private Set<Long> requiredLongSet(JsonNode data, String fieldName) {
        JsonNode field = data.get(fieldName);
        if (field == null || !field.isArray() || field.isEmpty()) {
            throw new PushEventProcessingException(fieldName + " must be a non-empty array.");
        }
        Set<Long> values = new LinkedHashSet<>();
        field.forEach(value -> {
            if (!value.canConvertToLong()) {
                throw new PushEventProcessingException(fieldName + " must contain integers only.");
            }
            values.add(value.longValue());
        });
        return values;
    }

    private void copyScalar(JsonNode source, Map<String, String> target, String fieldName) {
        JsonNode field = source.get(fieldName);
        if (field != null && !field.isNull() && !field.isContainerNode()) {
            target.put(fieldName, field.asText());
        }
    }

    private void putIfPresent(Map<String, String> data, String key, String value) {
        if (StringUtils.hasText(value)) {
            data.put(key, value);
        }
    }

    private record DispatchPlan(
            Collection<Long> recipientMemberIds,
            PushMessage message
    ) {
    }
}
