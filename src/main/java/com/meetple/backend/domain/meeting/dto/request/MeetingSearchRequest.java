package com.meetple.backend.domain.meeting.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MeetingSearchRequest(
        @NotBlank(message = "검색어를 입력해주세요.")
        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
        String keyword,

        @Size(max = 30, message = "카테고리는 30자 이하여야 합니다.")
        String category,

        @NotNull(message = "위도를 입력해주세요.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도를 입력해주세요.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude
) {
}
