package com.meetple.backend.global.exception;

import com.meetple.backend.global.response.ErrorStatus;

public class UnauthorizedException extends BaseException {
    public UnauthorizedException() {
        super(ErrorStatus.UNAUTHORIZED);
    }

    public UnauthorizedException(String message) {
        super(ErrorStatus.UNAUTHORIZED, message);
    }

    public UnauthorizedException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
