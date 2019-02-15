package com.swoval.files;

import static com.swoval.files.FileTreeRepositoryImpl.PATH_WATCHER_FACTORY;

import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.files.FileTreeDataViews.Converters;
import com.swoval.files.FileTreeDataViews.Entry;
import com.swoval.files.FileTreeViews.Observer;
import com.swoval.functional.Either;
import com.swoval.functional.Filter;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Provides factory methods for generating instances of {@link FileTreeRepository}. */
public class FileTreeRepositories {
  private FileTreeRepositories() {}
  /**
   * Create a default file tree repository. This method does not specify whether or not it follows
   * symlinks. If this matters prefer {@link FileTreeRepositories#noFollowSymlinks(Converter)} or
   * {@link FileTreeRepositories#followSymlinks(Converter)}.
   *
   * @param converter converts a path to the cached value type T
   * @param logger the logger
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static <T> FileTreeRepository<T> getDefault(
      final Converter<T> converter, final Logger logger) throws InterruptedException, IOException {
    return new FollowWrapper<>(
        FileTreeRepositoryImpl.get(true, converter, logger, PATH_WATCHER_FACTORY));
  }

  /**
   * Create a default file tree repository. This method does not specify whether or not it follows
   * symlinks. If this matters prefer {@link FileTreeRepositories#noFollowSymlinks(Converter)} or
   * {@link FileTreeRepositories#followSymlinks(Converter)}.
   *
   * @param converter converts a path to the cached value type T
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static <T> FileTreeRepository<T> getDefault(final Converter<T> converter)
      throws InterruptedException, IOException {
    return getDefault(converter, Loggers.getLogger());
  }

  /**
   * Create a default file tree repository. This method does not specify whether or not it follows
   * symlinks. If this matters prefer {@link FileTreeRepositories#noFollowSymlinks(Converter)} or
   * {@link FileTreeRepositories#followSymlinks(Converter)}.
   *
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static FileTreeRepository<TypedPath> getDefault()
      throws InterruptedException, IOException {
    return getDefault(Converters.IDENTITY, Loggers.getLogger());
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
    return new FollowWrapper<>(
        FileTreeRepositoryImpl.get(true, converter, logger, PATH_WATCHER_FACTORY));
  }
  /**
   * Create a file tree repository that follows symlinks.
   *
   * @param converter converts a path to the cached value type T
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static <T> FileTreeRepository<T> followSymlinks(final Converter<T> converter)
      throws InterruptedException, IOException {
    return followSymlinks(converter, Loggers.getLogger());
  }
  /**
   * Create a file tree repository that follows symlinks.
   *
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static FileTreeRepository<TypedPath> followSymlinks()
      throws InterruptedException, IOException {
    return followSymlinks(Converters.IDENTITY, Loggers.getLogger());
  }

  /**
   * Create a file tree repository that does not follow symlinks. Any symlinks in the results will
   * be treated like regular files though their {@link TypedPath} instance will return
   * `true` for {@link TypedPath#isSymbolicLink()}.
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
    return new NoFollowWrapper<>(
        FileTreeRepositoryImpl.get(false, converter, logger, PATH_WATCHER_FACTORY));
  }
  /**
   * Create a file tree repository that does not follow symlinks. Any symlinks in the results will
   * be treated like regular files though their {@link TypedPath} instance will return
   * `true` for {@link TypedPath#isSymbolicLink()}.
   *
   * @param converter converts a path to the cached value type T
   * @param <T> the value type of the cache entries
   * @return a file tree repository.
   * @throws InterruptedException if the path watcher can't be started.
   * @throws IOException if an instance of {@link java.nio.file.WatchService} cannot be created.
   */
  public static <T> NoFollowSymlinks<T> noFollowSymlinks(final Converter<T> converter)
      throws InterruptedException, IOException {
    return noFollowSymlinks(converter, Loggers.getLogger());
  }

  public interface FollowSymlinks<T> extends FileTreeRepository<T> {}

  public interface NoFollowSymlinks<T> extends FileTreeRepository<T> {}
}
