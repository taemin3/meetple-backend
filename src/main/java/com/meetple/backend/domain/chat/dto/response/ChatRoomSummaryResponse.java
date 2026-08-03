package com.meetple.backend.domain.chat.dto.response;

import com.meetple.backend.domain.meeting.entity.MeetingStatus;

public record ChatRoomSummaryResponse(
        Long roomId,
        Long meetingId,
        String meetingTitle,
        MeetingStatus meetingStatus,
        String thumbnailImageUrl,
        ChatMessageResponse lastMessage,
        long unreadCount,
        boolean canSend
) {
}
