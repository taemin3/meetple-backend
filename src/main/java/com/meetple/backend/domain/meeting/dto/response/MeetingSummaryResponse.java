package com.meetple.backend.domain.meeting.dto.response;

import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import com.meetple.backend.domain.meeting.repository.MeetingSummaryProjection;
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

    public static MeetingSummaryResponse from(
            MeetingSummaryProjection meeting,
            String thumbnailImageUrl
    ) {
        return new MeetingSummaryResponse(
                meeting.id(),
                meeting.hostId(),
                meeting.hostNickname(),
                meeting.categoryId(),
                meeting.categoryName(),
                meeting.title(),
                meeting.locationName(),
                meeting.address(),
                meeting.latitude().doubleValue(),
                meeting.longitude().doubleValue(),
                meeting.scheduledAt(),
                meeting.capacity(),
                meeting.currentPeople(),
                meeting.status(),
                thumbnailImageUrl
        );
    }
}
