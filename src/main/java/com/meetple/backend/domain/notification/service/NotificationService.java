package com.meetple.backend.domain.notification.service;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.notification.dto.response.NotificationResponse;
import com.meetple.backend.domain.notification.entity.Notification;
import com.meetple.backend.domain.notification.repository.NotificationRepository;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void notify(Member member, String type, String title, String message, Long meetingId) {
        notificationRepository.save(Notification.create(member, type, title, message, meetingId));
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
