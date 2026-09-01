package com.meetple.backend.domain.meeting.dto.response;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import java.time.LocalDateTime;

public record MeetingSummaryResponse(
        Long id,
        Long hostId,
        String hostNickname,
        Long categoryId,
        String categoryName,
        String title,
        String locationName,
        String address,
        Double latitude,
        Double longitude,
        LocalDateTime scheduledAt,
        Integer capacity,
        Integer currentPeople,
        MeetingStatus status,
        String thumbnailImageUrl
) {

    public static MeetingSummaryResponse from(Meeting meeting, String thumbnailImageUrl) {
        return new MeetingSummaryResponse(
                meeting.getId(),
                meeting.getHost().getId(),
                meeting.getHost().getNickname(),
                meeting.getCategory().getId(),
                meeting.getCategory().getName(),
                meeting.getTitle(),
                meeting.getLocationName(),
                meeting.getAddress(),
                meeting.getLatitude().doubleValue(),
                meeting.getLongitude().doubleValue(),
                meeting.getMeetingDate(),
                meeting.getMaxPeople(),
                meeting.getCurrentPeople(),
                meeting.getStatus(),
                thumbnailImageUrl
        );
    }
}
