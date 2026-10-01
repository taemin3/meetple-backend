package com.meetple.backend.domain.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class MeetingEmbeddingEventPublisherTest {

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Test
    void publishesCanonicalDocumentForCreatedMeeting() {
        Meeting meeting = meeting();
        var publisher = new MeetingEmbeddingEventPublisher(
                new AiEmbeddingProperties(true), outboxEventPublisher);

        publisher.publishCreated(meeting);

        var captor = ArgumentCaptor.forClass(OutboxEventRequest.class);
        verify(outboxEventPublisher).publish(captor.capture());
        OutboxEventRequest request = captor.getValue();
        assertThat(request.aggregateType()).isEqualTo("meeting");
        assertThat(request.aggregateId()).isEqualTo("10");
        assertThat(request.eventType()).isEqualTo("MEETING_EMBEDDING_REQUESTED");
        assertThat(request.topic()).isEqualTo(OutboxEventTopic.MEETING_EMBEDDING);
        assertThat(request.data()).isInstanceOf(MeetingEmbeddingDocument.class);
        MeetingEmbeddingDocument document = (MeetingEmbeddingDocument) request.data();
        assertThat(document.document()).contains(
                "제목: 주말 초보 러닝",
                "카테고리: 운동",
                "소개: 처음 달리는 분도 환영합니다.");
        assertThat(document.contentHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void skipsUnchangedDocumentAndDisabledFeature() {
        Meeting meeting = meeting();
        var enabledPublisher = new MeetingEmbeddingEventPublisher(
                new AiEmbeddingProperties(true), outboxEventPublisher);

        enabledPublisher.publishIfChanged(meeting, enabledPublisher.contentHash(meeting));
        new MeetingEmbeddingEventPublisher(new AiEmbeddingProperties(false), outboxEventPublisher)
                .publishCreated(meeting);

        verify(outboxEventPublisher, never()).publish(any());
    }

    private Meeting meeting() {
        Meeting meeting = Meeting.create(
                null,
                Category.create("운동"),
                "주말 초보 러닝",
                "처음 달리는 분도 환영합니다.",
                "한강 공원",
                "서울 영등포구",
                BigDecimal.valueOf(37.5),
                BigDecimal.valueOf(127.0),
                10,
                LocalDateTime.of(2026, 10, 3, 15, 0),
                null);
        ReflectionTestUtils.setField(meeting, "id", 10L);
        return meeting;
    }
}
