package com.meetple.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.notification.entity.Notification;
import com.meetple.backend.domain.notification.repository.NotificationRepository;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import com.meetple.backend.domain.outbox.event.OutboxEventTopic;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void notifyStoresNotificationAndPublishesVersionedOutboxEvent() {
        Member recipient = Member.createUser(
                "recipient@meetple.com",
                "encoded-password",
                "recipient",
                "Seoul"
        );
        ReflectionTestUtils.setField(recipient, "id", 7L);
        given(notificationRepository.save(any(Notification.class))).willAnswer(invocation -> {
            Notification notification = invocation.getArgument(0);
            ReflectionTestUtils.setField(notification, "id", 501L);
            return notification;
        });

        notificationService.notify(
                recipient,
                "PARTICIPATION_APPROVED",
                "참여 승인",
                "러닝 모임 참여가 승인되었습니다.",
                101L
        );

        ArgumentCaptor<OutboxEventRequest> requestCaptor = ArgumentCaptor.forClass(OutboxEventRequest.class);
        verify(outboxEventPublisher).publish(requestCaptor.capture());
        OutboxEventRequest request = requestCaptor.getValue();
        assertThat(request.aggregateType()).isEqualTo("notification");
        assertThat(request.aggregateId()).isEqualTo("501");
        assertThat(request.eventType()).isEqualTo("PARTICIPATION_APPROVED");
        assertThat(request.eventKey()).isEqualTo("member:7");
        assertThat(request.topic()).isEqualTo(OutboxEventTopic.PUSH_NOTIFICATION);
        assertThat(request.schemaVersion()).isEqualTo(1);
        assertThat(request.deduplicationKey()).isEqualTo("notification:501");

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) request.data();
        assertThat(data).containsEntry("recipientMemberId", 7L)
                .containsEntry("notificationId", 501L)
                .containsEntry("meetingId", 101L)
                .containsEntry("title", "참여 승인")
                .containsEntry("body", "러닝 모임 참여가 승인되었습니다.");
    }
}
