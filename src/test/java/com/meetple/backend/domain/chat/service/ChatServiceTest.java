package com.meetple.backend.domain.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.meetple.backend.domain.category.entity.Category;
import com.meetple.backend.domain.chat.dto.request.MarkChatRoomReadRequest;
import com.meetple.backend.domain.chat.dto.request.SendChatMessageRequest;
import com.meetple.backend.domain.chat.entity.ChatMessage;
import com.meetple.backend.domain.chat.entity.ChatReadState;
import com.meetple.backend.domain.chat.event.ChatMessageCreatedEvent;
import com.meetple.backend.domain.chat.repository.ChatMessageRepository;
import com.meetple.backend.domain.chat.repository.ChatReadStateRepository;
import com.meetple.backend.domain.chat.repository.ChatUnreadCountProjection;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private ChatReadStateRepository readStateRepository;

    @Mock
    private MeetingRepository meetingRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ChatAccessPolicy accessPolicy;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ChatService chatService;

    @Test
    void sendMessageAllocatesNextRoomSequenceAndTrimsContent() {
        Member host = member(1L, "host");
        Meeting meeting = meeting(10L, host);
        UUID clientMessageId = UUID.randomUUID();
        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(messageRepository.findByMeetingIdAndSenderIdAndClientMessageId(
                10L,
                1L,
                clientMessageId
        )).willReturn(Optional.empty());
        given(memberRepository.findById(1L)).willReturn(Optional.of(host));
        given(messageRepository.findTopByMeetingIdOrderByRoomSequenceDesc(10L))
                .willReturn(Optional.of(message(20L, meeting, host, 7L, UUID.randomUUID(), "previous")));
        given(messageRepository.saveAndFlush(any(ChatMessage.class)))
                .willAnswer(invocation -> {
                    ChatMessage saved = invocation.getArgument(0);
                    ReflectionTestUtils.setField(saved, "id", 21L);
                    return saved;
                });

        var response = chatService.sendMessage(
                1L,
                10L,
                new SendChatMessageRequest(clientMessageId, "  hello  ")
        );

        assertThat(response.sequence()).isEqualTo(8L);
        assertThat(response.content()).isEqualTo("hello");
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRoomSequence()).isEqualTo(8L);
        ArgumentCaptor<ChatReadState> readStateCaptor = ArgumentCaptor.forClass(ChatReadState.class);
        verify(readStateRepository).save(readStateCaptor.capture());
        assertThat(readStateCaptor.getValue().getLastReadSequence()).isEqualTo(8L);
        ArgumentCaptor<ChatMessageCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(ChatMessageCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().message()).isEqualTo(response);
    }

    @Test
    void duplicateClientMessageIdReturnsExistingMessageWithoutSavingAgain() {
        Member host = member(1L, "host");
        Meeting meeting = meeting(10L, host);
        UUID clientMessageId = UUID.randomUUID();
        ChatMessage existing = message(30L, meeting, host, 3L, clientMessageId, "hello");
        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(messageRepository.findByMeetingIdAndSenderIdAndClientMessageId(
                10L,
                1L,
                clientMessageId
        )).willReturn(Optional.of(existing));

        var response = chatService.sendMessage(
                1L,
                10L,
                new SendChatMessageRequest(clientMessageId, "hello")
        );

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.sequence()).isEqualTo(3L);
        verify(messageRepository, never()).saveAndFlush(any(ChatMessage.class));
    }

    @Test
    void beforeCursorReturnsMessagesInChronologicalOrder() {
        Member host = member(1L, "host");
        Meeting meeting = meeting(10L, host);
        given(accessPolicy.getAccessibleMeeting(1L, 10L)).willReturn(meeting);
        given(messageRepository.findByMeetingIdAndRoomSequenceLessThanOrderByRoomSequenceDesc(
                10L,
                5L,
                PageRequest.of(0, 3)
        )).willReturn(List.of(
                message(4L, meeting, host, 4L, UUID.randomUUID(), "four"),
                message(3L, meeting, host, 3L, UUID.randomUUID(), "three"),
                message(2L, meeting, host, 2L, UUID.randomUUID(), "two")
        ));

        var response = chatService.getMessages(1L, 10L, 5L, null, 2);

        assertThat(response.hasMore()).isTrue();
        assertThat(response.content()).extracting(item -> item.sequence())
                .containsExactly(3L, 4L);
        assertThat(response.oldestSequence()).isEqualTo(3L);
        assertThat(response.latestSequence()).isEqualTo(4L);
    }

    @Test
    void getRoomsLoadsSummariesInBatch() {
        Member host = member(1L, "host");
        Meeting firstMeeting = meeting(10L, host);
        Meeting secondMeeting = meeting(11L, host);
        PageRequest pageable = PageRequest.of(0, 20);
        ChatMessage latestMessage = message(
                20L,
                firstMeeting,
                host,
                2L,
                UUID.randomUUID(),
                "latest"
        );
        ChatUnreadCountProjection unreadCount = unreadCount(10L, 2L);
        given(meetingRepository.findChatAccessibleMeetings(1L, pageable))
                .willReturn(new PageImpl<>(List.of(firstMeeting, secondMeeting), pageable, 2));
        given(messageRepository.findLatestByMeetingIds(List.of(10L, 11L)))
                .willReturn(List.of(latestMessage));
        given(messageRepository.countUnreadByMeetingIds(1L, List.of(10L, 11L)))
                .willReturn(List.of(unreadCount));

        var response = chatService.getRooms(1L, pageable);

        assertThat(response.content()).hasSize(2);
        assertThat(response.content().get(0).lastMessage().sequence()).isEqualTo(2L);
        assertThat(response.content().get(0).unreadCount()).isEqualTo(2L);
        assertThat(response.content().get(1).lastMessage()).isNull();
        assertThat(response.content().get(1).unreadCount()).isZero();
        verify(messageRepository).findLatestByMeetingIds(List.of(10L, 11L));
        verify(messageRepository).countUnreadByMeetingIds(1L, List.of(10L, 11L));
    }

    @Test
    void afterCursorReturnsCatchUpMessagesInAscendingOrder() {
        Member host = member(1L, "host");
        Meeting meeting = meeting(10L, host);
        given(accessPolicy.getAccessibleMeeting(1L, 10L)).willReturn(meeting);
        given(messageRepository.findByMeetingIdAndRoomSequenceGreaterThanOrderByRoomSequenceAsc(
                10L,
                5L,
                PageRequest.of(0, 3)
        )).willReturn(List.of(
                message(6L, meeting, host, 6L, UUID.randomUUID(), "six"),
                message(7L, meeting, host, 7L, UUID.randomUUID(), "seven")
        ));

        var response = chatService.getMessages(1L, 10L, null, 5L, 2);

        assertThat(response.hasMore()).isFalse();
        assertThat(response.content()).extracting(item -> item.sequence())
                .containsExactly(6L, 7L);
    }

    @Test
    void markReadRejectsSequenceBeyondLatestMessage() {
        Member host = member(1L, "host");
        Meeting meeting = meeting(10L, host);
        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(messageRepository.findTopByMeetingIdOrderByRoomSequenceDesc(10L))
                .willReturn(Optional.of(message(
                        2L,
                        meeting,
                        host,
                        2L,
                        UUID.randomUUID(),
                        "latest"
                )));

        assertThatThrownBy(() -> chatService.markRead(
                1L,
                10L,
                new MarkChatRoomReadRequest(3L)
        ))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("lastReadSequence는 최신 메시지 순서를 초과할 수 없습니다.");
    }

    @Test
    void markReadNeverMovesCursorBackward() {
        Member host = member(1L, "host");
        Meeting meeting = meeting(10L, host);
        ChatReadState readState = ChatReadState.create(meeting, host, 5L);
        given(meetingRepository.findByIdForUpdate(10L)).willReturn(Optional.of(meeting));
        given(messageRepository.findTopByMeetingIdOrderByRoomSequenceDesc(10L))
                .willReturn(Optional.of(message(
                        6L,
                        meeting,
                        host,
                        6L,
                        UUID.randomUUID(),
                        "latest"
                )));
        given(readStateRepository.findByMeetingIdAndMemberId(10L, 1L))
                .willReturn(Optional.of(readState));
        given(readStateRepository.save(readState)).willReturn(readState);

        var response = chatService.markRead(1L, 10L, new MarkChatRoomReadRequest(3L));

        assertThat(response.lastReadSequence()).isEqualTo(5L);
        verify(meetingRepository).findByIdForUpdate(10L);
        verify(accessPolicy).ensureCanAccess(1L, meeting);
    }

    private ChatMessage message(
            Long id,
            Meeting meeting,
            Member sender,
            Long sequence,
            UUID clientMessageId,
            String content
    ) {
        ChatMessage message = ChatMessage.create(
                meeting,
                sender,
                sequence,
                clientMessageId,
                content
        );
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private ChatUnreadCountProjection unreadCount(Long meetingId, Long unreadCount) {
        return new ChatUnreadCountProjection() {
            @Override
            public Long getMeetingId() {
                return meetingId;
            }

            @Override
            public Long getUnreadCount() {
                return unreadCount;
            }
        };
    }

    private Meeting meeting(Long id, Member host) {
        Category category = Category.create("exercise");
        ReflectionTestUtils.setField(category, "id", 1L);
        Meeting meeting = Meeting.create(
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
        );
        ReflectionTestUtils.setField(meeting, "id", id);
        return meeting;
    }

    private Member member(Long id, String nickname) {
        Member member = Member.createUser(
                nickname + "@meetple.com",
                "encoded-password",
                nickname,
                "Seoul"
        );
        ReflectionTestUtils.setField(member, "id", id);
        return member;
    }
}
