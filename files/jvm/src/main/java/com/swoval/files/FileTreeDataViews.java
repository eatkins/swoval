package com.swoval.files;

import com.swoval.files.api.Observable;
import java.io.IOException;

/**
 * Provides functional interfaces for processing and managing instances of {@link FileTreeDataView}.
 */
public class FileTreeDataViews {

  /**
   * Provides callbacks to run when different types of file events are detected by the cache.
   *
   * @param <T> the type for the {@link CacheEntry} data
   */
  public interface CacheObserver<T> {

    /**
     * Callback to fire when a new path is created.
     *
     * @param newCacheEntry the {@link CacheEntry} for the newly created file
     */
    void onCreate(final CacheEntry<T> newCacheEntry);

    /**
     * Callback to fire when a path is deleted.
     *
     * @param oldCacheEntry the {@link CacheEntry} for the deleted.
     */
    void onDelete(final CacheEntry<T> oldCacheEntry);

    /**
     * Callback to fire when a path is modified.
     *
     * @param oldCacheEntry the {@link CacheEntry} for the updated path
     * @param newCacheEntry the {@link CacheEntry} for the deleted path
     */
    void onUpdate(final CacheEntry<T> oldCacheEntry, final CacheEntry<T> newCacheEntry);

    /**
     * Callback to fire when an error is encountered generating while updating a path.
     *
     * @param exception The exception thrown by the computation
     */
    void onError(final IOException exception);
  }

  /**
   * A file tree cache that can be monitored for events.
   *
   * @param <T> the type of data stored in the cache.
   */
  public interface ObservableCache<T> extends Observable<CacheEntry<T>> {
    /**
     * Add an observer of cache events.
     *
     * @param observer the observer to add
     * @return the handle to the observer.
     */
    int addCacheObserver(final CacheObserver<T> observer);
  }
}
