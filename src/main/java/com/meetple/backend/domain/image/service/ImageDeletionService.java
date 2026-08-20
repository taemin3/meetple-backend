package com.meetple.backend.domain.image.service;

import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class ImageDeletionService {

    private static final String EVENT_TYPE = "IMAGE_DELETE_REQUESTED";
    private static final int SCHEMA_VERSION = 1;

    private final OutboxEventPublisher outboxEventPublisher;

    public void schedule(String objectKey) {
        schedule(StringUtils.hasText(objectKey) ? List.of(objectKey) : List.of());
    }

    public void schedule(Collection<String> objectKeys) {
        objectKeys.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .map(this::toRequest)
                .forEach(outboxEventPublisher::publish);
    }

    private OutboxEventRequest toRequest(String objectKey) {
        return new OutboxEventRequest(
                "image",
                objectKey,
                EVENT_TYPE,
                objectKey,
                OutboxEventTopic.IMAGE_DELETION,
                SCHEMA_VERSION,
                "image-delete:" + UUID.randomUUID(),
                Map.of("objectKey", objectKey)
        );
    }
}
