package com.swoval.files.api;

/**
 * Generic Observer for an {@link Observable}.
 *
 * @param <T> the type under observation
 */
public interface Observer<T> {
  /**
   * Fired if the underlying {@link Observable} encounters an error
   *
   * @param t the error
   */
  void onError(final Throwable t);

  /**
   * Callback that is invoked whenever a change is detected by the {@link Observable}.
   *
   * @param t the changed instance
   */
  void onNext(final T t);
}
