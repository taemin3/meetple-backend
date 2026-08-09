package com.meetple.backend.domain.notification.service;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.notification.dto.response.NotificationResponse;
import com.meetple.backend.domain.notification.entity.Notification;
import com.meetple.backend.domain.notification.repository.NotificationRepository;
import com.meetple.backend.domain.outbox.service.OutboxEventPublisher;
import com.meetple.backend.domain.outbox.service.OutboxEventRequest;
import com.meetple.backend.domain.push.event.PushEventTopic;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private static final String AGGREGATE_TYPE = "notification";
    private static final int SCHEMA_VERSION = 1;

    private final NotificationRepository notificationRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void notify(Member member, String type, String title, String message, Long meetingId) {
        Notification notification = notificationRepository.save(
                Notification.create(member, type, title, message, meetingId)
        );
        Long notificationId = notification.getId();
        if (notificationId == null) {
            throw new IllegalStateException("Saved notification ID must not be null.");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recipientMemberId", member.getId());
        data.put("notificationId", notificationId);
        if (meetingId != null) {
            data.put("meetingId", meetingId);
        }
        data.put("title", title);
        data.put("body", message);

        outboxEventPublisher.publish(new OutboxEventRequest(
                AGGREGATE_TYPE,
                notificationId.toString(),
                type,
                "member:" + member.getId(),
                PushEventTopic.NOTIFICATION,
                SCHEMA_VERSION,
                "notification:" + notificationId,
                data
        ));
    }

    public PageResponse<NotificationResponse> getNotifications(Long memberId, Pageable pageable) {
        return PageResponse.from(
                notificationRepository.findByMemberId(memberId, pageable)
                        .map(NotificationResponse::from)
        );
    }

    @Transactional
    public NotificationResponse markRead(Long memberId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndMemberId(notificationId, memberId)
                .orElseThrow(() -> new NotFoundException("Notification not found."));
        notification.markRead();
        return NotificationResponse.from(notification);
    }
}
