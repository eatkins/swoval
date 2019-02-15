package com.swoval.files;

import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.logging.Logger;
import java.io.IOException;

/** Provides factory methods for generating instances of {@link FileTreeRepository}. */
public class FileTreeRepositories {
  private FileTreeRepositories() {}

  private static FileTreeRepositoryProvider provider =
      SwovalProviderImpl.getDefaultProvider().getFileTreeRepositoryProvider();

  /**
   * Create a default file tree repository that doesn't store a data value in the cache. The return
   * {@link FileTreeRepository} will follow symlinks. To set a data value or to control whether or
   * not to follow symlinks, see {@link FileTreeRepositories#noFollowSymlinks(Converter)} or {@link
   * FileTreeRepositories#followSymlinks(Converter)}
   *
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static FileTreeRepository<Object> getDefault() throws InterruptedException, IOException {
    return provider.getDefault();
  }

  /**
   * Create a file tree repository that follows symlinks.
   *
   * @param converter converts a path to the cached value type T
   * @param logger the logger
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static <T> FollowSymlinks<T> followSymlinks(
      final Converter<T> converter, final Logger logger) throws InterruptedException, IOException {
    return provider.followSymlinks(converter, logger);
  }

  /**
   * Create a file tree repository that does not follow symlinks. Any symlinks in the results will
   * be treated like regular files though their {@link TypedPath} instance will return `true` for
   * {@link TypedPath#isSymbolicLink()}.
   *
   * @param converter converts a path to the cached value type T
   * @param logger the logger
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static <T> NoFollowSymlinks<T> noFollowSymlinks(
      final Converter<T> converter, final Logger logger) throws InterruptedException, IOException {
    return provider.noFollowSymlinks(converter, logger);
  }

  public interface FollowSymlinks<T> extends FileTreeRepository<T> {}

  public interface NoFollowSymlinks<T> extends FileTreeRepository<T> {}
}
