package com.swoval.files.cache;

import java.io.IOException;

/**
 * Provides callbacks to run when different types of file events are detected by the cache.
 *
 * @param <T> the type for the {@link Entry} data
 */
public interface CacheObserver<T> {

  /**
   * Callback to fire when a new path is created.
   *
   * @param newCacheEntry the {@link Entry} for the newly created file
   */
  void onCreate(final Entry<T> newCacheEntry);

  /**
   * Callback to fire when a path is deleted.
   *
   * @param oldCacheEntry the {@link Entry} for the deleted.
   */
  void onDelete(final Entry<T> oldCacheEntry);

  /**
   * Callback to fire when a path is modified.
   *
   * @param oldCacheEntry the {@link Entry} for the updated path
   * @param newCacheEntry the {@link Entry} for the deleted path
   */
  void onUpdate(final Entry<T> oldCacheEntry, final Entry<T> newCacheEntry);

  /**
   * Callback to fire when an error is encountered generating while updating a path.
   *
   * @param exception The exception thrown by the computation
   */
  void onError(final IOException exception);
}
