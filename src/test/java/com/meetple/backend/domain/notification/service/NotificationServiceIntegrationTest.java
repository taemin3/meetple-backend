package com.meetple.backend.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.notification.entity.Notification;
import com.meetple.backend.domain.notification.repository.NotificationRepository;
import com.meetple.backend.domain.outbox.entity.OutboxEvent;
import com.meetple.backend.domain.outbox.repository.OutboxEventRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        outboxEventRepository.deleteAll();
        notificationRepository.deleteAll();
    }

    @Test
    void notifyCommitsNotificationAndOutboxEventTogether() {
        Member recipient = savedMember();

        notificationService.notify(
                recipient,
                "PARTICIPATION_APPROVED",
                "참여 승인",
                "러닝 모임 참여가 승인되었습니다.",
                101L
        );

        Notification notification = notificationRepository.findAll().getFirst();
        OutboxEvent outboxEvent = outboxEventRepository.findAll().getFirst();
        assertThat(outboxEvent.getAggregateType()).isEqualTo("notification");
        assertThat(outboxEvent.getAggregateId()).isEqualTo(notification.getId().toString());
        assertThat(outboxEvent.getEventType()).isEqualTo("PARTICIPATION_APPROVED");
        assertThat(outboxEvent.getEventKey()).isEqualTo("member:" + recipient.getId());
        assertThat(outboxEvent.getTopic()).isEqualTo("meetple.push.notification.v1");
        assertThat(outboxEvent.getDeduplicationKey()).isEqualTo("notification:" + notification.getId());
        assertThat(outboxEvent.getPayload().path("data").path("recipientMemberId").asLong())
                .isEqualTo(recipient.getId());
        assertThat(outboxEvent.getPayload().path("data").path("notificationId").asLong())
                .isEqualTo(notification.getId());
        assertThat(outboxEvent.getPayload().path("data").path("meetingId").asLong()).isEqualTo(101L);
    }

    @Test
    void businessRollbackRemovesNotificationAndOutboxEvent() {
        Member recipient = savedMember();

        transactionTemplate.executeWithoutResult(status -> {
            notificationService.notify(
                    recipient,
                    "PARTICIPATION_REJECTED",
                    "참여 거절",
                    "러닝 모임 참여 신청이 거절되었습니다.",
                    101L
            );
            status.setRollbackOnly();
        });

        assertThat(notificationRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    private Member savedMember() {
        String uniqueEmail = "push-" + UUID.randomUUID() + "@meetple.com";
        return memberRepository.save(
                Member.createUser(uniqueEmail, "encoded-password", "recipient", "Seoul")
        );
    }
}
