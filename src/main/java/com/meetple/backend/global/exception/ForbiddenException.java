package com.meetple.backend.global.exception;

import com.meetple.backend.global.response.ErrorStatus;

public class ForbiddenException extends BaseException {
  public ForbiddenException() {
    super(ErrorStatus.FORBIDDEN);
  }

  public ForbiddenException(String message) {
    super(ErrorStatus.FORBIDDEN, message);
  }

  public ForbiddenException(ErrorStatus errorStatus) {
    super(errorStatus);
  }
}
