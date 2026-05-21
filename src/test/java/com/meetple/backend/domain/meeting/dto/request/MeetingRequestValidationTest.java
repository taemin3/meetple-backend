package com.meetple.backend.domain.meeting.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MeetingRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void createMeetingRequestRejectsInvalidValues() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "",
                "",
                "",
                91.0,
                181.0,
                LocalDateTime.now().minusDays(1),
                1,
                ""
        );

        Set<ConstraintViolation<CreateMeetingRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "모임 제목을 입력해주세요.",
                        "카테고리를 선택해주세요.",
                        "장소명을 입력해주세요.",
                        "위도는 90 이하여야 합니다.",
                        "경도는 180 이하여야 합니다.",
                        "모임 일시는 현재 이후여야 합니다.",
                        "정원은 2명 이상이어야 합니다.",
                        "모임 소개를 입력해주세요."
                );
    }

    @Test
    void createMeetingRequestAcceptsValidValues() {
        CreateMeetingRequest request = new CreateMeetingRequest(
                "주말 러닝 모임",
                "운동",
                "한강공원",
                37.5219,
                126.9245,
                LocalDateTime.now().plusDays(7),
                10,
                "가볍게 뛰고 커피 마셔요."
        );

        Set<ConstraintViolation<CreateMeetingRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void nearbyMeetingSearchRequestRejectsInvalidRadius() {
        NearbyMeetingSearchRequest request = new NearbyMeetingSearchRequest(37.5219, 126.9245, 50, null);

        Set<ConstraintViolation<NearbyMeetingSearchRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("검색 반경은 100m 이상이어야 합니다.");
    }

    @Test
    void createMeetingParticipationRequestAcceptsBlankMessageBecauseItIsOptional() {
        CreateMeetingParticipationRequest request = new CreateMeetingParticipationRequest("");

        Set<ConstraintViolation<CreateMeetingParticipationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }
}
