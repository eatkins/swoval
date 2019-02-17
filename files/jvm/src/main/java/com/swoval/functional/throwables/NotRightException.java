package com.swoval.functional.throwables;

public class NotRightException extends Exception {
  private final String message;

  public NotRightException(final String message) {
    this.message = message;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
