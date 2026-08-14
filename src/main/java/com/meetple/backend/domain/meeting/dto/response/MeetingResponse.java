package com.meetple.backend.domain.meeting.dto.response;

import com.meetple.backend.domain.meeting.entity.Meeting;
import com.meetple.backend.domain.meeting.entity.MeetingStatus;
import java.time.LocalDateTime;
import java.util.List;

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
        LocalDateTime endsAt,
        Integer capacity,
        Integer currentPeople,
        MeetingStatus status,
        String cancelReason,
        String thumbnailImageUrl,
        List<String> imageUrls,
        List<String> imageObjectKeys,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public MeetingResponse(
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
            List<String> imageUrls,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this(
                id,
                hostId,
                hostNickname,
                categoryId,
                categoryName,
                title,
                description,
                locationName,
                address,
                latitude,
                longitude,
                scheduledAt,
                null,
                capacity,
                currentPeople,
                status,
                null,
                thumbnailImageUrl,
                imageUrls,
                List.of(),
                createdAt,
                updatedAt
        );
    }

    public static MeetingResponse from(Meeting meeting) {
        return from(meeting, List.of());
    }

    public static MeetingResponse from(Meeting meeting, List<String> imageUrls) {
        return from(meeting, imageUrls, List.of());
    }

    public static MeetingResponse from(
            Meeting meeting,
            List<String> imageUrls,
            List<String> imageObjectKeys
    ) {
        List<String> resolvedImageUrls = imageUrls == null ? List.of() : imageUrls;
        List<String> resolvedImageObjectKeys = imageObjectKeys == null ? List.of() : imageObjectKeys;
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
                meeting.getEndDate(),
                meeting.getMaxPeople(),
                meeting.getCurrentPeople(),
                meeting.getStatus(),
                meeting.getCancelReason(),
                resolveThumbnailImageUrl(meeting, resolvedImageUrls),
                resolvedImageUrls,
                resolvedImageObjectKeys,
                meeting.getCreatedAt(),
                meeting.getUpdatedAt()
        );
    }

    private static String resolveThumbnailImageUrl(Meeting meeting, List<String> imageUrls) {
        if (!imageUrls.isEmpty() && hasText(imageUrls.get(0))) {
            return imageUrls.get(0);
        }
        return meeting.getCategory().getDefaultImageUrl();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
