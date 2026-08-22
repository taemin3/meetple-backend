package com.meetple.backend.domain.auth.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void signupRequestRejectsInvalidValues() {
        SignupRequest request = new SignupRequest("not-email", "", "1234", "", List.of());

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains(
                        "올바른 이메일 형식이 아닙니다.",
                        "비밀번호는 8자 이상 64자 이하여야 합니다.",
                        "닉네임을 입력해주세요.",
                        "필수 약관 확인 정보가 필요합니다."
                );
    }

    @Test
    void loginRequestAcceptsValidValues() {
        LoginRequest request = new LoginRequest("user@meetple.com", "password123");

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    @Test
    void signupRequestRejectsNullLegalDocumentItem() {
        List<SignupLegalDocumentRequest> legalDocuments = Arrays.asList(null, null, null);
        SignupRequest request = new SignupRequest(
                "user@meetple.com",
                "signup-verification-token",
                "password123",
                "tester",
                legalDocuments
        );

        Set<ConstraintViolation<SignupRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(ConstraintViolation::getMessage)
                .contains("약관 확인 항목은 비어 있을 수 없습니다.");
    }
}
