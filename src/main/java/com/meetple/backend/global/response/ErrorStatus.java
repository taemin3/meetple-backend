package com.meetple.backend.global.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public enum ErrorStatus {

    // 400 BAD_REQUEST
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.",10001),
    MISSING_EMAIL_VERIFICATION_EXCEPTION(HttpStatus.BAD_REQUEST, "이메일 인증을 진행해주세요.",10002),

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다.",10004),
    LEGAL_DOCUMENTS_REQUIRED(HttpStatus.BAD_REQUEST, "필수 약관 확인 정보가 올바르지 않습니다.", 10005),
    LEGAL_DOCUMENT_VERSION_INVALID(HttpStatus.BAD_REQUEST, "최신 약관을 다시 확인해주세요.", 10006),

    // 401 UNAUTHORIZED
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다.", 10101),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.", 12410),
    NOT_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED,"토큰의 타입이 액세스 토큰이 아닙니다.",12413),
    NOT_SERVICE_TOKEN(HttpStatus.UNAUTHORIZED,"토큰의 타입이 서비스 토큰(내부 통신용 토큰)이 아닙니다.",12414),
    INVALID_TOKEN_ROLE(HttpStatus.UNAUTHORIZED,"유효하지 않은 토큰 내 권한 정보입니다. (토큰 권한 불일치)",12415),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다.", 12411),


    // 403 FORBIDDEN
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.",10301),
    ACCESS_DENIED(HttpStatus.FORBIDDEN,"접근 권한이 없어 접근이 거부되었습니다.",10302),

    // 404 NOT_FOUND
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다.",10401),

    // 409 CONFLICT
    CONFLICT(HttpStatus.CONFLICT, "충돌이 발생했습니다.",10901),
    OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "리소스가 갱신되었습니다. 다시 시도해 주세요.",10902),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.",10903),

    // 500 INTERNAL_SERVER_ERROR
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.",10501),
    EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "외부 API 호출 중 오류가 발생했습니다.",10502);

    private final HttpStatus httpStatus;
    private final String message;
    private final int code;


    public int getStatusCode() {
        return this.httpStatus.value();
    }

}
