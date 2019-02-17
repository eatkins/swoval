package com.swoval.functional.throwables;

public class NotLeftException extends Exception {
  private final String message;

  public NotLeftException(final String message) {
    this.message = message;
  }

  @Override
  public String getMessage() {
    return message;
  }
}
