package com.meetple.backend.domain.chat.service;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import com.meetple.backend.domain.meeting.repository.MeetingParticipationRepository;
import com.meetple.backend.domain.meeting.repository.MeetingRepository;
import com.meetple.backend.global.exception.BadRequestException;
import com.meetple.backend.global.exception.ForbiddenException;
import com.meetple.backend.global.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatAccessPolicy {

    private static final String MEETING_NOT_FOUND_MESSAGE = "모임을 찾을 수 없습니다.";
    private static final String CHAT_ACCESS_DENIED_MESSAGE =
            "모임 주최자와 승인된 참여자만 채팅방에 입장할 수 있습니다.";
    private static final String CHAT_READ_ONLY_MESSAGE =
            "종료되거나 취소된 모임의 채팅방에서는 메시지를 보낼 수 없습니다.";
    private static final String CHAT_REALTIME_CLOSED_MESSAGE =
            "취소된 모임의 실시간 채팅에는 연결할 수 없습니다.";

    private final MeetingRepository meetingRepository;
    private final MeetingParticipationRepository participationRepository;

    public Meeting getAccessibleMeeting(Long memberId, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException(MEETING_NOT_FOUND_MESSAGE));
        ensureCanAccess(memberId, meeting);
        return meeting;
    }

    public Meeting getRealtimeAccessibleMeeting(Long memberId, Long meetingId) {
        Meeting meeting = getAccessibleMeeting(memberId, meetingId);
        if (meeting.getStatus() == MeetingStatus.CANCELED) {
            throw new ForbiddenException(CHAT_REALTIME_CLOSED_MESSAGE);
        }
        return meeting;
    }

    public void ensureCanAccess(Long memberId, Meeting meeting) {
        if (!canAccess(memberId, meeting)) {
            throw new ForbiddenException(CHAT_ACCESS_DENIED_MESSAGE);
        }
    }

    public boolean canAccess(Long memberId, Meeting meeting) {
        return meeting.isHostedBy(memberId)
                || participationRepository.existsByMeetingIdAndMemberIdAndStatus(
                        meeting.getId(),
                        memberId,
                        ParticipationStatus.APPROVED
                );
    }

    public void ensureCanSend(Meeting meeting) {
        if (!canSend(meeting)) {
            throw new BadRequestException(CHAT_READ_ONLY_MESSAGE);
        }
    }

    public boolean canSend(Meeting meeting) {
        return meeting.getStatus() == MeetingStatus.RECRUITING
                || meeting.getStatus() == MeetingStatus.FULL;
    }
}
