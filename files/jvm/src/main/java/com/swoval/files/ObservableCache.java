package com.swoval.files;

import com.swoval.files.api.Observable;
import com.swoval.files.cache.CacheObserver;
import com.swoval.files.cache.Event;

/**
 * A file tree cache that can be monitored for events.
 *
 * @param <T> the type of data stored in the cache.
 */
public interface ObservableCache<T> extends Observable<Event<T>> {
  /**
   * Add an observer of cache events.
   *
   * @param observer the observer to add
   * @return the handle to the observer.
   */
  int addCacheObserver(final CacheObserver<T> observer);
}
