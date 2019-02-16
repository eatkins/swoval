package com.swoval.files.api;

/**
 * Represents an event emitter for some generic event type, `T`.
 *
 * @param <T> the type of events.
 */
public interface Observable<T> {

  /**
   * Add an observer of events.
   *
   * @param observer the observer to add
   * @return the handle to the observer.
   */
  int addObserver(final Observer<? super T> observer);

  /**
   * Remove an observer.
   *
   * @param handle the handle that was returned by addObserver
   */
  void removeObserver(final int handle);
}
