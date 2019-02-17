package com.swoval.functional;

import com.swoval.files.TypedPath;
import java.io.IOException;

/**
 * Converts a Path into an arbitrary value to be cached.
 *
 * @param <R> the generic type generated from the path.
 */
public interface Converter<R> {

  /**
   * Convert the typedPath to a value.
   *
   * @param typedPath the typedPath to convert
   * @return the converted value
   * @throws IOException when the value can't be computed
   */
  R apply(final TypedPath typedPath) throws IOException;
}
