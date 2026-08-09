package com.meetple.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.category.repository.CategoryRepository;
import com.meetple.backend.domain.chat.dto.request.SendChatMessageRequest;
import com.meetple.backend.domain.chat.repository.ChatMessageRepository;
import com.meetple.backend.domain.chat.repository.ChatReadStateRepository;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.domain.outbox.entity.OutboxEvent;
import com.meetple.backend.domain.outbox.repository.OutboxEventRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
class ChatServiceIntegrationTest {

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private ChatReadStateRepository readStateRepository;

    @Autowired
    private MeetingParticipationRepository participationRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        outboxEventRepository.deleteAll();
        readStateRepository.deleteAll();
        messageRepository.deleteAll();
        participationRepository.deleteAll();
        meetingRepository.deleteAll();
        categoryRepository.deleteAll();
        memberRepository.deleteAll();
    }

    @Test
    void sendMessageCommitsChatMessageAndOutboxEventTogether() {
        ChatFixture fixture = savedFixture();

        var result = chatService.sendMessage(
                fixture.host().getId(),
                fixture.meeting().getId(),
                new SendChatMessageRequest(UUID.randomUUID(), "곧 도착합니다.")
        );

        assertThat(messageRepository.count()).isOne();
        OutboxEvent event = outboxEventRepository.findAll().getFirst();
        assertThat(event.getAggregateType()).isEqualTo("chat_message");
        assertThat(event.getAggregateId()).isEqualTo(result.message().id().toString());
        assertThat(event.getEventType()).isEqualTo("CHAT_MESSAGE_CREATED");
        assertThat(event.getEventKey()).isEqualTo("room:" + fixture.meeting().getId());
        assertThat(event.getTopic()).isEqualTo("meetple.push.chat.v1");
        assertThat(event.getDeduplicationKey())
                .isEqualTo("chat-message:" + result.message().id());
        assertThat(event.getPayload().path("data").path("recipientMemberIds").get(0).asLong())
                .isEqualTo(fixture.participant().getId());
        assertThat(event.getPayload().path("data").path("senderMemberId").asLong())
                .isEqualTo(fixture.host().getId());
        assertThat(event.getPayload().path("data").path("chatMessageId").asLong())
                .isEqualTo(result.message().id());
        assertThat(event.getPayload().path("data").path("roomSequence").asLong()).isEqualTo(1L);
    }

    @Test
    void businessRollbackRemovesChatMessageAndOutboxEvent() {
        ChatFixture fixture = savedFixture();

        transactionTemplate.executeWithoutResult(status -> {
            chatService.sendMessage(
                    fixture.host().getId(),
                    fixture.meeting().getId(),
                    new SendChatMessageRequest(UUID.randomUUID(), "롤백할 메시지")
            );
            status.setRollbackOnly();
        });

        assertThat(messageRepository.count()).isZero();
        assertThat(readStateRepository.count()).isZero();
        assertThat(outboxEventRepository.count()).isZero();
    }

    private ChatFixture savedFixture() {
        Member host = memberRepository.save(member("host"));
        Member participant = memberRepository.save(member("participant"));
        Category category = categoryRepository.save(
                Category.create("chat-" + UUID.randomUUID())
        );
        Meeting meeting = meetingRepository.save(Meeting.create(
                host,
                category,
                "Weekend running",
                "Run together.",
                "Yeouido Park",
                "Seoul",
                new BigDecimal("37.521900"),
                new BigDecimal("126.924500"),
                10,
                LocalDateTime.now().plusDays(1),
                null
        ));
        MeetingParticipation participation = MeetingParticipation.apply(
                meeting,
                participant,
                "함께 달리고 싶어요."
        );
        participation.approve();
        participationRepository.save(participation);
        return new ChatFixture(host, participant, meeting);
    }

    private Member member(String nickname) {
        return Member.createUser(
                nickname + "-" + UUID.randomUUID() + "@meetple.com",
                "encoded-password",
                nickname,
                "Seoul"
        );
    }

    private record ChatFixture(Member host, Member participant, Meeting meeting) {
    }
}
