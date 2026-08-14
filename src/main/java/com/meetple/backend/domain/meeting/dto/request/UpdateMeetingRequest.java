package com.meetple.backend.domain.meeting.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public final class UpdateMeetingRequest {

    @Size(max = 50, message = "모임 제목은 50자 이하여야 합니다.")
    private String title;

    @Size(max = 30, message = "카테고리는 30자 이하여야 합니다.")
    private String category;

    @Size(max = 100, message = "장소명은 100자 이하여야 합니다.")
    private String locationName;

    @Size(max = 255, message = "주소는 255자 이하여야 합니다.")
    private String address;

    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
    private Double longitude;

    @Future(message = "모임 일시는 현재 이후여야 합니다.")
    private LocalDateTime scheduledAt;

    @Min(value = 2, message = "정원은 2명 이상이어야 합니다.")
    @Max(value = 100, message = "정원은 100명 이하여야 합니다.")
    private Integer capacity;

    @Size(max = 1000, message = "모임 소개는 1000자 이하여야 합니다.")
    private String description;

    @Size(max = 10, message = "이미지는 최대 10장까지 등록할 수 있습니다.")
    private List<
            @NotBlank(message = "이미지 object key를 입력해주세요.")
            @Size(max = 255, message = "이미지 object key는 255자 이하여야 합니다.")
            String> imageObjectKeys;

    private LocalDateTime endsAt;

    @JsonIgnore
    private boolean endsAtProvided;

    public UpdateMeetingRequest(
            String title,
            String category,
            String locationName,
            String address,
            Double latitude,
            Double longitude,
            LocalDateTime scheduledAt,
            Integer capacity,
            String description,
            List<String> imageObjectKeys,
            LocalDateTime endsAt
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
                imageObjectKeys
        );
        this.endsAt = endsAt;
        this.endsAtProvided = true;
    }

    public UpdateMeetingRequest(
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
        this.title = title;
        this.category = category;
        this.locationName = locationName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.scheduledAt = scheduledAt;
        this.capacity = capacity;
        this.description = description;
        this.imageObjectKeys = imageObjectKeys;
    }

    public UpdateMeetingRequest(
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
                null
        );
    }

    @JsonSetter("endsAt")
    public void setEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
        this.endsAtProvided = true;
    }
}
