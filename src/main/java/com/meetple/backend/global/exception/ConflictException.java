package com.meetple.backend.global.exception;

import com.meetple.backend.global.response.ErrorStatus;

public class ConflictException extends BaseException {

    public ConflictException() {
        super(ErrorStatus.CONFLICT);
    }

    public ConflictException(String message) {
        super(ErrorStatus.CONFLICT, message);
    }

    public ConflictException(ErrorStatus errorStatus) {
        super(errorStatus);
    }
}
