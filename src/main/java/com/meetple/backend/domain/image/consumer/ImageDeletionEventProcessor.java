package com.meetple.backend.domain.image.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.image.config.ImageStorageProperties;
import com.meetple.backend.domain.image.entity.ImageUploadPurpose;
import com.meetple.backend.domain.image.storage.ImageStorageClient;
import com.meetple.backend.domain.outbox.event.OutboxEventEnvelope;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ImageDeletionEventProcessor {

    private static final String SUPPORTED_EVENT_TYPE = "IMAGE_DELETE_REQUESTED";
    private static final int SUPPORTED_SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;
    private final ImageStorageClient imageStorageClient;
    private final ImageStorageProperties properties;

    public void process(String payload) {
        OutboxEventEnvelope envelope = parseEnvelope(payload);
        validateEnvelope(envelope);
        String objectKey = requiredObjectKey(envelope.data());
        validateImageIdentity(envelope, objectKey);
        validateDeletableObjectKey(objectKey);
        imageStorageClient.deleteObject(objectKey);
    }

    private OutboxEventEnvelope parseEnvelope(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new NonRetryableImageDeletionEventException(
                    "Image deletion event payload must not be null or blank."
            );
        }
        try {
            OutboxEventEnvelope envelope = objectMapper.readValue(payload, OutboxEventEnvelope.class);
            if (envelope == null) {
                throw new NonRetryableImageDeletionEventException(
                        "Image deletion event payload must not be JSON null."
                );
            }
            return envelope;
        } catch (JsonProcessingException exception) {
            throw new NonRetryableImageDeletionEventException(
                    "Invalid image deletion event JSON.",
                    exception
            );
        }
    }

    private void validateEnvelope(OutboxEventEnvelope envelope) {
        if (envelope.eventId() == null
                || !SUPPORTED_EVENT_TYPE.equals(envelope.eventType())
                || envelope.schemaVersion() != SUPPORTED_SCHEMA_VERSION
                || envelope.data() == null
                || !envelope.data().isObject()) {
            throw new NonRetryableImageDeletionEventException(
                    "Unsupported image deletion event contract."
            );
        }
    }

    private String requiredObjectKey(JsonNode data) {
        JsonNode field = data.get("objectKey");
        if (field == null || !field.isTextual() || !StringUtils.hasText(field.textValue())) {
            throw new NonRetryableImageDeletionEventException(
                    "objectKey must be a non-blank string."
            );
        }
        return field.textValue().trim();
    }

    private void validateImageIdentity(OutboxEventEnvelope envelope, String objectKey) {
        if (!"image".equals(envelope.aggregateType()) || !objectKey.equals(envelope.aggregateId())) {
            throw new NonRetryableImageDeletionEventException(
                    "Image deletion event identity does not match objectKey."
            );
        }
    }

    private void validateDeletableObjectKey(String objectKey) {
        String keyPrefix = properties.keyPrefix();
        boolean deletable = Arrays.stream(ImageUploadPurpose.values())
                .map(purpose -> keyPrefix + "/" + purpose.pathSegment() + "/")
                .anyMatch(objectKey::startsWith);
        if (!deletable) {
            throw new NonRetryableImageDeletionEventException(
                    "Image deletion objectKey is outside an upload path."
            );
        }
    }
}
