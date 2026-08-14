package com.meetple.backend.domain.meeting.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public record CreateMeetingRequest(
        @NotBlank(message = "모임 제목을 입력해주세요.")
        @Size(max = 50, message = "모임 제목은 50자 이하여야 합니다.")
        String title,

        @NotBlank(message = "카테고리를 선택해주세요.")
        @Size(max = 30, message = "카테고리는 30자 이하여야 합니다.")
        String category,

        @NotBlank(message = "장소명을 입력해주세요.")
        @Size(max = 100, message = "장소명은 100자 이하여야 합니다.")
        String locationName,

        @NotBlank(message = "주소를 입력해주세요.")
        @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
        String address,

        @NotNull(message = "위도를 입력해주세요.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        Double latitude,

        @NotNull(message = "경도를 입력해주세요.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        Double longitude,

        @NotNull(message = "모임 일시를 입력해주세요.")
        @Future(message = "모임 일시는 현재 이후여야 합니다.")
        LocalDateTime scheduledAt,

        @NotNull(message = "정원을 입력해주세요.")
        @Min(value = 2, message = "정원은 2명 이상이어야 합니다.")
        @Max(value = 100, message = "정원은 100명 이하여야 합니다.")
        Integer capacity,

        @NotBlank(message = "모임 소개를 입력해주세요.")
        @Size(max = 1000, message = "모임 소개는 1000자 이하여야 합니다.")
        String description,

        @Size(max = 10, message = "이미지는 최대 10장까지 등록할 수 있습니다.")
        List<
                @NotBlank(message = "이미지 object key를 입력해주세요.")
                @Size(max = 255, message = "이미지 object key는 255자 이하여야 합니다.")
                String> imageObjectKeys,

        LocalDateTime endsAt
) {

    public CreateMeetingRequest(
            String title,
            String category,
            String locationName,
            String address,
            Double latitude,
            Double longitude,
            LocalDateTime scheduledAt,
            Integer capacity,
            String description,
            List<String> imageObjectKeys
    ) {
        this(
                title,
                category,
                locationName,
                address,
                latitude,
                longitude,
                scheduledAt,
                capacity,
                description,
                imageObjectKeys,
                null
        );
    }

    public CreateMeetingRequest(
            String title,
            String category,
            String locationName,
            String address,
            Double latitude,
            Double longitude,
            LocalDateTime scheduledAt,
            Integer capacity,
            String description
    ) {
        this(
                title,
                category,
                locationName,
                address,
                latitude,
                longitude,
                scheduledAt,
                capacity,
                description,
                null,
                null
        );
    }
}
