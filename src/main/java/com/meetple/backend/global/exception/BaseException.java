package com.meetple.backend.global.exception;

import com.meetple.backend.global.response.ErrorStatus;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class BaseException extends RuntimeException {
    private final ErrorStatus errorStatus;
    private final String responseMessage;

    public BaseException(ErrorStatus errorStatus) {
        this(errorStatus, errorStatus.getMessage());
    }

    public BaseException(ErrorStatus errorStatus, String responseMessage) {
        super(responseMessage);
        this.errorStatus = errorStatus;
        this.responseMessage = responseMessage;
    }

    public HttpStatus getHttpStatus() {
        return this.errorStatus.getHttpStatus();
    }

    public int getStatusCode() {
        return this.errorStatus.getStatusCode();
    }

    public int getErrorCode() {
        return this.errorStatus.getCode();
    }
}
