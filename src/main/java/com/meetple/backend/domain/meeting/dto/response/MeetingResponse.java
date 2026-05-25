package com.meetple.backend.domain.meeting.dto.response;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import java.time.LocalDateTime;

public record MeetingResponse(
        Long id,
        Long hostId,
        String hostNickname,
        Long categoryId,
        String categoryName,
        String title,
        String description,
        String locationName,
        String address,
        Double latitude,
        Double longitude,
        LocalDateTime scheduledAt,
        Integer capacity,
        Integer currentPeople,
        MeetingStatus status,
        String thumbnailImageUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static MeetingResponse from(Meeting meeting) {
        return new MeetingResponse(
                meeting.getId(),
                meeting.getHost().getId(),
                meeting.getHost().getNickname(),
                meeting.getCategory().getId(),
                meeting.getCategory().getName(),
                meeting.getTitle(),
                meeting.getContent(),
                meeting.getLocationName(),
                meeting.getAddress(),
                meeting.getLatitude().doubleValue(),
                meeting.getLongitude().doubleValue(),
                meeting.getMeetingDate(),
                meeting.getMaxPeople(),
                meeting.getCurrentPeople(),
                meeting.getStatus(),
                meeting.getThumbnailImageUrl(),
                meeting.getCreatedAt(),
                meeting.getUpdatedAt()
        );
    }
}
