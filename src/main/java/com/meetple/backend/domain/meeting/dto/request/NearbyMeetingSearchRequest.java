package com.meetple.backend.domain.meeting.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NearbyMeetingSearchRequest(
        @NotNull(message = "위도를 입력해주세요.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도를 입력해주세요.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude,

        @NotNull(message = "검색 반경을 입력해주세요.")
        @Min(value = 100, message = "검색 반경은 100m 이상이어야 합니다.")
        @Max(value = 50000, message = "검색 반경은 50000m 이하여야 합니다.")
        Integer radiusMeters,

        @Size(max = 30, message = "카테고리는 30자 이하여야 합니다.")
        String category
) {
}
