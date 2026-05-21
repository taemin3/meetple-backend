package com.meetple.backend.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.meetple.backend.global.response.ErrorStatus;
import org.junit.jupiter.api.Test;

class BaseExceptionTest {

    @Test
    void defaultExceptionUsesErrorStatusFields() {
        BadRequestException exception = new BadRequestException();

        assertThat(exception.getErrorStatus()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.getHttpStatus()).isEqualTo(ErrorStatus.BAD_REQUEST.getHttpStatus());
        assertThat(exception.getStatusCode()).isEqualTo(400);
        assertThat(exception.getErrorCode()).isEqualTo(10001);
        assertThat(exception.getResponseMessage()).isEqualTo(ErrorStatus.BAD_REQUEST.getMessage());
    }

    @Test
    void customMessageKeepsErrorStatusCode() {
        BadRequestException exception = new BadRequestException("커스텀 요청 오류입니다.");

        assertThat(exception.getErrorStatus()).isEqualTo(ErrorStatus.BAD_REQUEST);
        assertThat(exception.getStatusCode()).isEqualTo(400);
        assertThat(exception.getErrorCode()).isEqualTo(10001);
        assertThat(exception.getResponseMessage()).isEqualTo("커스텀 요청 오류입니다.");
    }
}
