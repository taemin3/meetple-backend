package com.meetple.backend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.domain.meeting.dto.request.NearbyMeetingSearchRequest;
import com.meetple.backend.global.response.ApiResponse;
import com.meetple.backend.global.response.ErrorStatus;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void baseExceptionReturnsUnifiedErrorBody() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBaseException(
                new NotFoundException(ErrorStatus.NOT_FOUND)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(404);
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(10401);
        assertThat(response.getBody().getMessage()).isEqualTo(ErrorStatus.NOT_FOUND.getMessage());
    }

    @Test
    void runtimeExceptionReturnsInternalServerErrorBody() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleRuntimeException(
                new RuntimeException("boom")
        );

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(500);
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorStatus.INTERNAL_SERVER_ERROR.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("런타임 오류가 발생했습니다.");
    }

    @Test
    void constraintViolationExceptionReturnsValidationErrorBody() {
        Set<ConstraintViolation<NearbyMeetingSearchRequest>> violations = validator.validate(
                new NearbyMeetingSearchRequest(91.0, 126.9245, 1000, null)
        );

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolationException(
                new ConstraintViolationException(violations)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus()).isEqualTo(400);
        assertThat(response.getBody().getSuccess()).isFalse();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorStatus.VALIDATION_ERROR.getCode());
        assertThat(response.getBody().getMessage()).isEqualTo("위도는 90 이하여야 합니다.");
    }
}
