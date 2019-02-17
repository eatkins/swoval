package com.swoval.files;

import com.swoval.files.impl.CachedDirectoryImpl;
import com.swoval.functional.Either;
import java.io.IOException;

/**
 * Container class for {@link CachedDirectoryImpl} entries. Contains both the path to which the path
 * corresponds along with a data value.
 *
 * @param <T> The value wrapped in the CacheEntry
 */
public interface CacheEntry<T> extends Comparable<CacheEntry<T>> {

  /**
   * Returns the {@link TypedPath} associated with this entry.
   *
   * @return the {@link TypedPath}.
   */
  TypedPath getTypedPath();
  /**
   * Return the value associated with this entry. jjj
   *
   * @return the value associated with this entry.
   */
  Either<IOException, T> getValue();
}
