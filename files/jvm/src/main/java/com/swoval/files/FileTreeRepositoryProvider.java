package com.swoval.files;

import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.files.FileTreeRepositories.FollowSymlinks;
import com.swoval.files.FileTreeRepositories.NoFollowSymlinks;
import java.io.IOException;

public interface FileTreeRepositoryProvider {
  /**
   * Create a default file tree repository that doesn't store a data value in the cache. The return
   * {@link FileTreeRepository} will follow symlinks. To set a data value or to control whether or
   * not to follow symlinks, see {@link SwovalProvider#noFollowSymlinks(Converter)} or {@link
   * SwovalProvider#followSymlinks(Converter)}
   *
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  FileTreeRepository<Object> getDefault() throws InterruptedException, IOException;

  /**
   * Create a file tree repository that follows symlinks.
   *
   * @param converter converts a path to the cached value type T
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  <T> FollowSymlinks<T> followSymlinks(final Converter<T> converter)
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
  <T> NoFollowSymlinks<T> noFollowSymlinks(final Converter<T> converter)
      throws InterruptedException, IOException;
}
