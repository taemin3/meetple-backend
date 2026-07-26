package com.meetple.backend.domain.notification.dto.response;

import com.meetple.backend.domain.notification.entity.Notification;
import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String type,
        String title,
        String message,
        Long meetingId,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getMeetingId(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }
}
