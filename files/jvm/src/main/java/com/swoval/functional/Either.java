package com.swoval.functional;

import com.swoval.functional.throwables.NotLeftException;
import com.swoval.functional.throwables.NotRightException;

public interface Either<L, R> {
  boolean isRight();

  L leftValue() throws NotLeftException;

  R rightValue() throws NotRightException;
}
