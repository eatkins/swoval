package com.swoval.files;

import com.swoval.files.impl.SwovalProviderImpl;

/**
 * Provides static methods returning instances of the various view interfaces defined throughout
 * this package.
 */
public class FileTreeViews {

  private FileTreeViews() {}

  private static final FileTreeViewProvider fileTreeViewProvider =
      SwovalProviderImpl.getDefaultProvider().getFileTreeViewProvider();

  public static FollowSymlinks followSymlinks() {
    return fileTreeViewProvider.followSymlinks();
  }

  public static NoFollowSymlinks noFollowSymlinks() {
    return fileTreeViewProvider.noFollowSymlinks();
  }

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

  public interface FollowSymlinks extends FileTreeView {};

  public interface NoFollowSymlinks extends FileTreeView {};
}
