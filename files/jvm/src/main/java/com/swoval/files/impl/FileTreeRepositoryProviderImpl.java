package com.swoval.files.impl;

import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.files.TypedPath;
import com.swoval.functional.IOFunction;
import com.swoval.files.CacheEntry;
import com.swoval.files.FileTreeRepositories.FollowSymlinks;
import com.swoval.files.FileTreeRepositories.NoFollowSymlinks;
import com.swoval.files.FileTreeRepository;
import com.swoval.files.FileTreeRepositoryProvider;
import com.swoval.files.PathWatcherProvider;
import com.swoval.files.PathWatchers.Event;
import com.swoval.files.api.Observer;
import com.swoval.files.api.PathWatcher;
import com.swoval.functional.Either;
import com.swoval.functional.Filter;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

class FileTreeRepositoryProviderImpl implements FileTreeRepositoryProvider {
  private final Logger logger;
  private final PathWatcherProvider pathWatcherProvider;

  FileTreeRepositoryProviderImpl(
      final PathWatcherProvider pathWatcherProvider, final Logger logger) {
    this.logger = logger;
    this.pathWatcherProvider = pathWatcherProvider;
  }

  @Override
  public <T> FollowSymlinks<T> followSymlinks(final IOFunction<TypedPath, T> converter)
      throws InterruptedException, IOException {
    return new FollowWrapper<>(get(true, converter, logger, pathWatcherProvider));
  }

  @Override
  public <T> NoFollowSymlinks<T> noFollowSymlinks(final IOFunction<TypedPath, T> converter)
      throws InterruptedException, IOException {
    return new NoFollowWrapper<>(get(false, converter, logger, pathWatcherProvider));
  }

  static <T> FileTreeRepository<T> get(
      final boolean followLinks,
      final IOFunction<TypedPath, T> converter,
      final Logger logger,
      final PathWatcherProvider pathWatcherProvider)
      throws InterruptedException, IOException {
    final SymlinkWatcher symlinkWatcher =
        followLinks ? new SymlinkWatcher(pathWatcherProvider.noFollowSymlinks(), logger) : null;
    final Executor callbackExecutor = Executor.make("FileTreeRepository-callback-executor", logger);
    final FileCacheDirectoryTree<T> tree =
        new FileCacheDirectoryTree<>(converter, callbackExecutor, symlinkWatcher, false, logger);
    final PathWatcher<Event> pathWatcher = pathWatcherProvider.noFollowSymlinks();
    pathWatcher.addObserver(fileTreeObserver(tree, logger));
    final FileCachePathWatcher<T> watcher = new FileCachePathWatcher<>(tree, pathWatcher);
    return new FileTreeRepositoryImpl<>(tree, watcher, logger);
  }

  private static Observer<Event> fileTreeObserver(
      final FileCacheDirectoryTree<?> tree, final Logger logger) {
    return new Observer<Event>() {
      @Override
      public void onError(final Throwable t) {
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          logger.error("Error while monitoring the file system " + t);
        }
      }

      @Override
      public void onNext(final Event event) {
        tree.handleEvent(event);
      }
    };
  }

  private static class Wrapper<T> implements FileTreeRepository<T> {
    private final FileTreeRepository<T> delegate;

    Wrapper(final FileTreeRepository<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Either<IOException, Boolean> register(final Path path, final int maxDepth) {
      return delegate.register(path, maxDepth);
    }

    @Override
    public void unregister(final Path path) {
      delegate.unregister(path);
    }

    @Override
    public List<CacheEntry<T>> list(
        final Path path, final int maxDepth, final Filter<? super CacheEntry<T>> filter)
        throws IOException {
      return delegate.list(path, maxDepth, filter);
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public int addObserver(final Observer<? super CacheEntry<T>> observer) {
      return delegate.addObserver(observer);
    }

    @Override
    public void removeObserver(final int handle) {
      delegate.removeObserver(handle);
    }

    @Override
    public int addCacheObserver(final CacheObserver<T> observer) {
      return delegate.addCacheObserver(observer);
    }
  }

  private static final class NoFollowWrapper<T> extends Wrapper<T> implements NoFollowSymlinks<T> {
    NoFollowWrapper(final FileTreeRepository<T> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      return "NoFollowSymlinksFileTreeRepository@" + System.identityHashCode(this);
    }
  }

  private static final class FollowWrapper<T> extends Wrapper<T> implements FollowSymlinks<T> {
    FollowWrapper(final FileTreeRepository<T> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      return "SymlinkFollowingFileTreeRepository@" + System.identityHashCode(this);
    }
  }
}
