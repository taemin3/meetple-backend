package com.meetple.backend.domain.meeting.dto.response;

import com.meetple.backend.domain.meeting.entity.MeetingParticipation;
import com.meetple.backend.domain.meeting.entity.ParticipationStatus;
import java.time.LocalDateTime;

public record MeetingParticipationResponse(
        Long id,
        Long meetingId,
        String meetingTitle,
        Long memberId,
        String memberNickname,
        ParticipationStatus status,
        String message,
        LocalDateTime reviewedAt,
        LocalDateTime canceledAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MeetingParticipationResponse from(MeetingParticipation participation) {
        return new MeetingParticipationResponse(
                participation.getId(),
                participation.getMeeting().getId(),
                participation.getMeeting().getTitle(),
                participation.getMember().getId(),
                participation.getMember().getNickname(),
                participation.getStatus(),
                participation.getMessage(),
                participation.getReviewedAt(),
                participation.getCanceledAt(),
                participation.getCreatedAt(),
                participation.getUpdatedAt()
        );
    }
}
