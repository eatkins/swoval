package com.swoval.files;

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
