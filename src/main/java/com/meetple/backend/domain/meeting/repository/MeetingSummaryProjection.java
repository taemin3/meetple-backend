package com.meetple.backend.domain.meeting.repository;

import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MeetingSummaryProjection(
        Long id,
        Long hostId,
        String hostNickname,
        Long categoryId,
        String categoryName,
        String title,
        String locationName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        LocalDateTime scheduledAt,
        Integer capacity,
        Integer currentPeople,
        MeetingStatus status,
        String thumbnailImageObjectKey,
        String categoryDefaultImageUrl
) {
}
