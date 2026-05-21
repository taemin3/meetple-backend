package com.meetple.backend.global.exception;

import com.meetple.backend.global.response.ErrorStatus;

public class BadRequestException extends BaseException {
  public BadRequestException() {
    super(ErrorStatus.BAD_REQUEST);
  }

  public BadRequestException(String message) {
    super(ErrorStatus.BAD_REQUEST, message);
  }

  public BadRequestException(ErrorStatus errorStatus) {
    super(errorStatus);
  }
}
