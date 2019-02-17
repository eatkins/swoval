package com.swoval.functional;

import java.io.IOException;

/**
 * A one argument function that may throw an IOException.
 *
 * @param <T> the function input type
 * @param <R> the generic type generated from the path.
 */
public interface IOFunction<T, R> {

  /**
   * Convert the typedPath to a value.
   *
   * @param t the function input
   * @return the converted value
   * @throws IOException when the value can't be computed
   */
  R apply(final T t) throws IOException;
}
