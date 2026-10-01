package com.meetple.backend.domain.ai;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.meetple.backend.domain.outbox.event.OutboxEventEnvelope;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MeetingEmbeddingEventProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private MeetingEmbeddingClient client;

    @Mock
    private MeetingEmbeddingStore store;

    private MeetingEmbeddingEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new MeetingEmbeddingEventProcessor(objectMapper, client, store);
    }

    @Test
    void createsAndStoresEmbeddingForValidEvent() throws Exception {
        MeetingEmbeddingDocument document = document();
        var embedding = new ArrayList<>(Collections.nCopies(1536, 0.0));
        embedding.set(0, 1.0);
        when(client.create(document.document()))
                .thenReturn(new MeetingEmbeddingClient.Response("text-embedding-3-small", embedding));

        processor.process(payload(document));

        verify(store).upsertIfCurrent(document, "text-embedding-3-small", embedding);
    }

    @Test
    void rejectsTamperedDocumentBeforeCallingAiServer() throws Exception {
        MeetingEmbeddingDocument valid = document();
        MeetingEmbeddingDocument tampered = new MeetingEmbeddingDocument(
                valid.meetingId(), valid.title(), valid.category(), valid.locationName(),
                valid.address(), valid.description(), valid.document() + " 변조", valid.contentHash());

        assertThatThrownBy(() -> processor.process(payload(tampered)))
                .isInstanceOf(NonRetryableMeetingEmbeddingException.class)
                .hasMessage("모임 임베딩 문서 해시가 일치하지 않습니다.");
        verify(client, never()).create(anyString());
        verify(store, never()).upsertIfCurrent(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList());
    }

    private MeetingEmbeddingDocument document() {
        return MeetingEmbeddingDocument.from(
                10L, "주말 초보 러닝", "운동", "한강 공원", "서울 영등포구",
                "처음 달리는 분도 환영합니다.");
    }

    private String payload(MeetingEmbeddingDocument document) throws Exception {
        return objectMapper.writeValueAsString(new OutboxEventEnvelope(
                UUID.randomUUID(),
                MeetingEmbeddingEventPublisher.EVENT_TYPE,
                MeetingEmbeddingEventPublisher.SCHEMA_VERSION,
                Instant.now().toString(),
                MeetingEmbeddingEventPublisher.AGGREGATE_TYPE,
                Long.toString(document.meetingId()),
                objectMapper.valueToTree(document)));
    }
}
