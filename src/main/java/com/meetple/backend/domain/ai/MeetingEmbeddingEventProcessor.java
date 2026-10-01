package com.meetple.backend.domain.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.outbox.event.OutboxEventEnvelope;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class MeetingEmbeddingEventProcessor {
    private final ObjectMapper objectMapper;
    private final MeetingEmbeddingClient client;
    private final MeetingEmbeddingStore store;

    public void process(String payload) {
        OutboxEventEnvelope envelope = parseEnvelope(payload);
        validateEnvelope(envelope);
        MeetingEmbeddingDocument data = parseData(envelope);
        validateData(envelope, data);
        MeetingEmbeddingClient.Response response = client.create(data.document());
        validateResponse(response);
        store.upsertIfCurrent(data, response.embeddingModel(), response.embedding());
    }

    private OutboxEventEnvelope parseEnvelope(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new NonRetryableMeetingEmbeddingException("모임 임베딩 이벤트가 비어 있습니다.");
        }
        try {
            OutboxEventEnvelope envelope = objectMapper.readValue(payload, OutboxEventEnvelope.class);
            if (envelope == null) {
                throw new NonRetryableMeetingEmbeddingException("모임 임베딩 이벤트가 JSON null입니다.");
            }
            return envelope;
        } catch (JsonProcessingException exception) {
            throw new NonRetryableMeetingEmbeddingException(
                    "모임 임베딩 이벤트 JSON이 올바르지 않습니다.", exception
            );
        }
    }

    private void validateEnvelope(OutboxEventEnvelope envelope) {
        try {
            Instant.parse(envelope.occurredAt());
        } catch (RuntimeException exception) {
            throw new NonRetryableMeetingEmbeddingException(
                    "모임 임베딩 이벤트 발생 시각이 올바르지 않습니다.", exception
            );
        }
        if (envelope.eventId() == null
                || !MeetingEmbeddingEventPublisher.EVENT_TYPE.equals(envelope.eventType())
                || envelope.schemaVersion() != MeetingEmbeddingEventPublisher.SCHEMA_VERSION
                || !MeetingEmbeddingEventPublisher.AGGREGATE_TYPE.equals(envelope.aggregateType())
                || !StringUtils.hasText(envelope.aggregateId())
                || envelope.data() == null
                || !envelope.data().isObject()) {
            throw new NonRetryableMeetingEmbeddingException("지원하지 않는 모임 임베딩 이벤트입니다.");
        }
    }

    private MeetingEmbeddingDocument parseData(OutboxEventEnvelope envelope) {
        try {
            return objectMapper.treeToValue(envelope.data(), MeetingEmbeddingDocument.class);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableMeetingEmbeddingException(
                    "모임 임베딩 이벤트 데이터가 올바르지 않습니다.", exception
            );
        }
    }

    private void validateData(OutboxEventEnvelope envelope, MeetingEmbeddingDocument data) {
        if (data == null
                || data.meetingId() <= 0
                || !Long.toString(data.meetingId()).equals(envelope.aggregateId())
                || !validText(data.title(), 50)
                || !validText(data.category(), 30)
                || !validText(data.locationName(), 100)
                || !validText(data.address(), 255)
                || !validText(data.description(), 1000)
                || !validText(data.document(), 3000)
                || data.contentHash() == null
                || !data.contentHash().matches("[0-9a-f]{64}")) {
            throw new NonRetryableMeetingEmbeddingException("모임 임베딩 이벤트 필드가 올바르지 않습니다.");
        }
        MeetingEmbeddingDocument expected = MeetingEmbeddingDocument.from(
                data.meetingId(), data.title(), data.category(), data.locationName(),
                data.address(), data.description()
        );
        if (!expected.document().equals(data.document())
                || !expected.contentHash().equals(data.contentHash())) {
            throw new NonRetryableMeetingEmbeddingException("모임 임베딩 문서 해시가 일치하지 않습니다.");
        }
    }

    private void validateResponse(MeetingEmbeddingClient.Response response) {
        if (response == null) {
            throw new MeetingEmbeddingProcessingException(
                    "AI 임베딩 응답이 비어 있습니다.", new IllegalStateException("null response")
            );
        }
        try {
            AiSearchToolController.validateEmbedding(response.embedding(), response.embeddingModel());
        } catch (RuntimeException exception) {
            throw new NonRetryableMeetingEmbeddingException(
                    "AI 임베딩 응답이 올바르지 않습니다.", exception
            );
        }
    }

    private boolean validText(String value, int maxLength) {
        return StringUtils.hasText(value) && value.length() <= maxLength;
    }
}
