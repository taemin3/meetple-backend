package com.meetple.backend.global.exception;

import com.meetple.backend.global.response.ErrorStatus;

public class NotFoundException extends BaseException {
  public NotFoundException() {
    super(ErrorStatus.NOT_FOUND);
  }

  public NotFoundException(String message) {
    super(ErrorStatus.NOT_FOUND, message);
  }

  public NotFoundException(ErrorStatus errorStatus) {
    super(errorStatus);
  }
}
