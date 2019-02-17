package com.swoval.files;

import com.swoval.files.api.Observable;
import com.swoval.files.impl.CachedDirectoryImpl;
import com.swoval.functional.Either;
import java.io.IOException;

/**
 * Provides functional interfaces for processing and managing instances of {@link FileTreeDataView}.
 */
public class FileTreeDataViews {

  /**
   * Container class for {@link CachedDirectoryImpl} entries. Contains both the path to which the
   * path corresponds along with a data value.
   *
   * @param <T> The value wrapped in the Entry
   */
  public interface Entry<T> extends Comparable<Entry<T>> {

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

  /**
   * Provides callbacks to run when different types of file events are detected by the cache.
   *
   * @param <T> the type for the {@link Entry} data
   */
  public interface CacheObserver<T> {

    /**
     * Callback to fire when a new path is created.
     *
     * @param newEntry the {@link Entry} for the newly created file
     */
    void onCreate(final Entry<T> newEntry);

    /**
     * Callback to fire when a path is deleted.
     *
     * @param oldEntry the {@link Entry} for the deleted.
     */
    void onDelete(final Entry<T> oldEntry);

    /**
     * Callback to fire when a path is modified.
     *
     * @param oldEntry the {@link Entry} for the updated path
     * @param newEntry the {@link Entry} for the deleted path
     */
    void onUpdate(final Entry<T> oldEntry, final Entry<T> newEntry);

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
  public interface ObservableCache<T> extends Observable<Entry<T>> {
    /**
     * Add an observer of cache events.
     *
     * @param observer the observer to add
     * @return the handle to the observer.
     */
    int addCacheObserver(final CacheObserver<T> observer);
  }
}
