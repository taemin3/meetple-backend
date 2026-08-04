package com.meetple.backend.domain.chat.service;

import com.meetple.backend.domain.chat.dto.request.MarkChatRoomReadRequest;
import com.meetple.backend.domain.chat.dto.request.SendChatMessageRequest;
import com.meetple.backend.domain.chat.dto.response.ChatMessagePageResponse;
import com.meetple.backend.domain.chat.dto.response.ChatMessageResponse;
import com.meetple.backend.domain.chat.dto.response.ChatReadStateResponse;
import com.meetple.backend.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.meetple.backend.domain.chat.entity.ChatMessage;
import com.meetple.backend.domain.chat.entity.ChatReadState;
import com.meetple.backend.domain.chat.repository.ChatMessageRepository;
import com.meetple.backend.domain.chat.repository.ChatReadStateRepository;
import com.meetple.backend.domain.chat.repository.ChatUnreadCountProjection;
import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.domain.member.entity.Member;
import com.meetple.backend.domain.member.repository.MemberRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.NotFoundException;
import com.meetple.backend.global.response.PageResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ChatMessageRepository messageRepository;
    private final ChatReadStateRepository readStateRepository;
    private final MeetingRepository meetingRepository;
    private final MemberRepository memberRepository;
    private final ChatAccessPolicy accessPolicy;

    public PageResponse<ChatRoomSummaryResponse> getRooms(Long memberId, Pageable pageable) {
        validatePageable(pageable);
        Page<Meeting> meetings = meetingRepository.findChatAccessibleMeetings(memberId, pageable);
        List<Long> meetingIds = meetings.stream().map(Meeting::getId).toList();
        Map<Long, ChatMessageResponse> lastMessages = getLastMessages(meetingIds);
        Map<Long, Long> unreadCounts = getUnreadCounts(memberId, meetingIds);

        return PageResponse.from(meetings.map(meeting -> toRoomSummary(
                meeting,
                lastMessages.get(meeting.getId()),
                unreadCounts.getOrDefault(meeting.getId(), 0L)
        )));
    }

    public ChatMessagePageResponse getMessages(
            Long memberId,
            Long meetingId,
            Long beforeSequence,
            Long afterSequence,
            int size
    ) {
        accessPolicy.getAccessibleMeeting(memberId, meetingId);
        validateMessageCursor(beforeSequence, afterSequence, size);

        PageRequest limit = PageRequest.of(0, size + 1);
        List<ChatMessage> fetched;
        boolean ascending;

        if (afterSequence != null) {
            fetched = messageRepository
                    .findByMeetingIdAndRoomSequenceGreaterThanOrderByRoomSequenceAsc(
                            meetingId,
                            afterSequence,
                            limit
                    );
            ascending = true;
        } else if (beforeSequence != null) {
            fetched = messageRepository
                    .findByMeetingIdAndRoomSequenceLessThanOrderByRoomSequenceDesc(
                            meetingId,
                            beforeSequence,
                            limit
                    );
            ascending = false;
        } else {
            fetched = messageRepository.findByMeetingIdOrderByRoomSequenceDesc(meetingId, limit);
            ascending = false;
        }

        boolean hasMore = fetched.size() > size;
        List<ChatMessage> selected = new ArrayList<>(
                fetched.subList(0, Math.min(size, fetched.size()))
        );
        if (!ascending) {
            Collections.reverse(selected);
        }

        return ChatMessagePageResponse.from(
                selected.stream().map(ChatMessageResponse::from).toList(),
                hasMore
        );
    }

    @Transactional
    public ChatMessageSendResult sendMessage(
            Long memberId,
            Long meetingId,
            SendChatMessageRequest request
    ) {
        validateMessageRequest(request);

        Meeting meeting = getAccessibleMeetingForUpdate(memberId, meetingId);
        accessPolicy.ensureCanSend(meeting);

        Optional<ChatMessage> existingMessage =
                messageRepository.findByMeetingIdAndSenderIdAndClientMessageId(
                        meetingId,
                        memberId,
                        request.clientMessageId()
                );
        boolean created = existingMessage.isEmpty();
        ChatMessage message = existingMessage
                .orElseGet(() -> saveMessage(memberId, meeting, request));
        advanceReadState(
                meeting,
                memberId,
                message::getSender,
                message.getRoomSequence()
        );
        return new ChatMessageSendResult(ChatMessageResponse.from(message), created);
    }

    @Transactional
    public ChatReadStateResponse markRead(
            Long memberId,
            Long meetingId,
            MarkChatRoomReadRequest request
    ) {
        Meeting meeting = getAccessibleMeetingForUpdate(memberId, meetingId);
        long latestSequence = messageRepository.findTopByMeetingIdOrderByRoomSequenceDesc(meetingId)
                .map(ChatMessage::getRoomSequence)
                .orElse(0L);
        if (request.lastReadSequence() > latestSequence) {
            throw new BadRequestException(
                    "lastReadSequence는 최신 메시지 순서를 초과할 수 없습니다."
            );
        }

        ChatReadState saved = advanceReadState(
                meeting,
                memberId,
                () -> getMember(memberId),
                request.lastReadSequence()
        );

        return new ChatReadStateResponse(meetingId, memberId, saved.getLastReadSequence());
    }

    private ChatRoomSummaryResponse toRoomSummary(
            Meeting meeting,
            ChatMessageResponse lastMessage,
            long unreadCount
    ) {
        return new ChatRoomSummaryResponse(
                meeting.getId(),
                meeting.getId(),
                meeting.getTitle(),
                meeting.getStatus(),
                meeting.getThumbnailImageUrl(),
                lastMessage,
                unreadCount,
                accessPolicy.canSend(meeting)
        );
    }

    private ChatMessage saveMessage(
            Long memberId,
            Meeting meeting,
            SendChatMessageRequest request
    ) {
        Member sender = getMember(memberId);
        long nextSequence = messageRepository.findTopByMeetingIdOrderByRoomSequenceDesc(meeting.getId())
                .map(ChatMessage::getRoomSequence)
                .orElse(0L) + 1;
        ChatMessage message = ChatMessage.create(
                meeting,
                sender,
                nextSequence,
                request.clientMessageId(),
                request.content()
        );
        return messageRepository.saveAndFlush(message);
    }

    private Meeting getAccessibleMeetingForUpdate(Long memberId, Long meetingId) {
        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new NotFoundException("모임을 찾을 수 없습니다."));
        accessPolicy.ensureCanAccess(memberId, meeting);
        return meeting;
    }

    private ChatReadState advanceReadState(
            Meeting meeting,
            Long memberId,
            Supplier<Member> memberSupplier,
            Long sequence
    ) {
        ChatReadState readState = readStateRepository
                .findByMeetingIdAndMemberId(meeting.getId(), memberId)
                .orElseGet(() -> ChatReadState.create(meeting, memberSupplier.get(), 0L));
        readState.markRead(sequence);
        return readStateRepository.save(readState);
    }

    private Map<Long, ChatMessageResponse> getLastMessages(List<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.findLatestByMeetingIds(meetingIds).stream()
                .map(ChatMessageResponse::from)
                .collect(Collectors.toMap(ChatMessageResponse::roomId, Function.identity()));
    }

    private Map<Long, Long> getUnreadCounts(Long memberId, List<Long> meetingIds) {
        if (meetingIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.countUnreadByMeetingIds(memberId, meetingIds).stream()
                .collect(Collectors.toMap(
                        ChatUnreadCountProjection::getMeetingId,
                        ChatUnreadCountProjection::getUnreadCount
                ));
    }

    private Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("회원을 찾을 수 없습니다."));
    }

    private void validatePageable(Pageable pageable) {
        if (pageable.getPageSize() < 1 || pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new BadRequestException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        if (pageable.getSort().isSorted()) {
            throw new BadRequestException("채팅방 목록은 최근 활동순으로만 정렬할 수 있습니다.");
        }
    }

    private void validateMessageCursor(Long beforeSequence, Long afterSequence, int size) {
        if (beforeSequence != null && afterSequence != null) {
            throw new BadRequestException(
                    "beforeSequence와 afterSequence는 동시에 사용할 수 없습니다."
            );
        }
        if (beforeSequence != null && beforeSequence < 1) {
            throw new BadRequestException("beforeSequence는 1 이상이어야 합니다.");
        }
        if (afterSequence != null && afterSequence < 0) {
            throw new BadRequestException("afterSequence는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("메시지 페이지 크기는 1 이상 100 이하여야 합니다.");
        }
    }

    private void validateMessageRequest(SendChatMessageRequest request) {
        if (request == null || request.clientMessageId() == null) {
            throw new BadRequestException("clientMessageId를 입력해주세요.");
        }
        if (request.content() == null || request.content().trim().isEmpty()) {
            throw new BadRequestException("메시지 내용을 입력해주세요.");
        }
        if (request.content().length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("메시지 내용은 1000자 이하여야 합니다.");
        }
    }
}
