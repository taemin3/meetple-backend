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
        return PageResponse.from(meetings.map(meeting -> toRoomSummary(memberId, meeting)));
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
    public ChatMessageResponse sendMessage(
            Long memberId,
            Long meetingId,
            SendChatMessageRequest request
    ) {
        validateMessageRequest(request);

        Meeting meeting = meetingRepository.findByIdForUpdate(meetingId)
                .orElseThrow(() -> new NotFoundException("모임을 찾을 수 없습니다."));
        accessPolicy.ensureCanAccess(memberId, meeting);
        accessPolicy.ensureCanSend(meeting);

        return messageRepository.findByMeetingIdAndSenderIdAndClientMessageId(
                        meetingId,
                        memberId,
                        request.clientMessageId()
                )
                .map(ChatMessageResponse::from)
                .orElseGet(() -> saveMessage(memberId, meeting, request));
    }

    @Transactional
    public ChatReadStateResponse markRead(
            Long memberId,
            Long meetingId,
            MarkChatRoomReadRequest request
    ) {
        Meeting meeting = accessPolicy.getAccessibleMeeting(memberId, meetingId);
        long latestSequence = messageRepository.findTopByMeetingIdOrderByRoomSequenceDesc(meetingId)
                .map(ChatMessage::getRoomSequence)
                .orElse(0L);
        if (request.lastReadSequence() > latestSequence) {
            throw new BadRequestException(
                    "lastReadSequence는 최신 메시지 순서를 초과할 수 없습니다."
            );
        }

        ChatReadState readState = readStateRepository.findByMeetingIdAndMemberId(meetingId, memberId)
                .orElseGet(() -> ChatReadState.create(meeting, getMember(memberId), 0L));
        readState.markRead(request.lastReadSequence());
        ChatReadState saved = readStateRepository.save(readState);

        return new ChatReadStateResponse(meetingId, memberId, saved.getLastReadSequence());
    }

    private ChatRoomSummaryResponse toRoomSummary(Long memberId, Meeting meeting) {
        ChatMessageResponse lastMessage = messageRepository
                .findTopByMeetingIdOrderByRoomSequenceDesc(meeting.getId())
                .map(ChatMessageResponse::from)
                .orElse(null);
        long lastReadSequence = readStateRepository
                .findByMeetingIdAndMemberId(meeting.getId(), memberId)
                .map(ChatReadState::getLastReadSequence)
                .orElse(0L);
        long unreadCount = messageRepository.countByMeetingIdAndRoomSequenceGreaterThan(
                meeting.getId(),
                lastReadSequence
        );

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

    private ChatMessageResponse saveMessage(
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
        return ChatMessageResponse.from(messageRepository.saveAndFlush(message));
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
        if (request.content().trim().length() > MAX_MESSAGE_LENGTH) {
            throw new BadRequestException("메시지 내용은 1000자 이하여야 합니다.");
        }
    }
}
