package com.meetple.backend.domain.meeting.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record UpdateMeetingRequest(
        @Size(max = 50, message = "모임 제목은 50자 이하여야 합니다.")
        String title,

        @Size(max = 30, message = "카테고리는 30자 이하여야 합니다.")
        String category,

        @Size(max = 100, message = "장소명은 100자 이하여야 합니다.")
        String locationName,

        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude,

        @Future(message = "모임 일시는 현재 이후여야 합니다.")
        LocalDateTime scheduledAt,

        @Min(value = 2, message = "정원은 2명 이상이어야 합니다.")
        @Max(value = 100, message = "정원은 100명 이하여야 합니다.")
        Integer capacity,

        @Size(max = 1000, message = "모임 소개는 1000자 이하여야 합니다.")
        String description
) {
}
