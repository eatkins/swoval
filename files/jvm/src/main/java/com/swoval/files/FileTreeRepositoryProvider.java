package com.swoval.files;

import com.swoval.functional.IOFunction;
import com.swoval.files.FileTreeRepositories.FollowSymlinks;
import com.swoval.files.FileTreeRepositories.NoFollowSymlinks;
import java.io.IOException;

public interface FileTreeRepositoryProvider {
  /**
   * Create a file tree repository that follows symlinks.
   *
   * @param converter converts a path to the cached value type T
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  <T> FollowSymlinks<T> followSymlinks(final IOFunction<TypedPath, T> converter)
      throws InterruptedException, IOException;

  /**
   * Create a file tree repository that does not follow symlinks. Any symlinks in the results will
   * be treated like regular files though their {@link TypedPath} instance will return `true` for
   * {@link TypedPath#isSymbolicLink()}.
   *
   * @param converter converts a path to the cached value type T
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  <T> NoFollowSymlinks<T> noFollowSymlinks(final IOFunction<TypedPath, T> converter)
      throws InterruptedException, IOException;
}
