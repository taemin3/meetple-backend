package com.meetple.backend.domain.ai;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class MeetingEmbeddingEventPublisher {
    static final String AGGREGATE_TYPE = "meeting";
    static final String EVENT_TYPE = "MEETING_EMBEDDING_REQUESTED";
    static final int SCHEMA_VERSION = 1;

    private final AiEmbeddingProperties properties;
    private final OutboxEventPublisher outboxEventPublisher;

    public String contentHash(Meeting meeting) {
        return MeetingEmbeddingDocument.from(meeting).contentHash();
    }

    public void publishIfChanged(Meeting meeting, String previousContentHash) {
        if (!properties.enabled()) return;
        MeetingEmbeddingDocument data = MeetingEmbeddingDocument.from(meeting);
        if (data.contentHash().equals(previousContentHash)) return;
        outboxEventPublisher.publish(new OutboxEventRequest(
                AGGREGATE_TYPE,
                Long.toString(meeting.getId()),
                EVENT_TYPE,
                Long.toString(meeting.getId()),
                OutboxEventTopic.MEETING_EMBEDDING,
                SCHEMA_VERSION,
                "meeting-embedding:" + meeting.getId() + ":" + UUID.randomUUID(),
                data
        ));
    }

    public void publishCreated(Meeting meeting) {
        publishIfChanged(meeting, null);
    }
}
