package com.swoval.files.impl;

import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.files.FileTreeDataViews.Entry;
import com.swoval.files.FileTreeRepositories.FollowSymlinks;
import com.swoval.files.FileTreeRepositories.NoFollowSymlinks;
import com.swoval.files.FileTreeRepository;
import com.swoval.files.FileTreeRepositoryProvider;
import com.swoval.files.FileTreeViews.Observer;
import com.swoval.files.PathWatcher;
import com.swoval.files.PathWatchers;
import com.swoval.files.PathWatchers.Event;
import com.swoval.files.TypedPath;
import com.swoval.functional.Either;
import com.swoval.functional.Filter;
import com.swoval.files.impl.functional.IOFunction;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

class FileTreeRepositoryProviderImpl implements FileTreeRepositoryProvider {

  @Override
  public FileTreeRepository<Object> getDefault() throws InterruptedException, IOException {
    return followSymlinks(Converters.UNIT_CONVERTER);
  }

  @Override
  public <T> FollowSymlinks<T> followSymlinks(final Converter<T> converter)
      throws InterruptedException, IOException {
    return new FollowWrapper<>(get(true, converter, Loggers.getLogger(), PATH_WATCHER_FACTORY));
  }

  @Override
  public <T> NoFollowSymlinks<T> noFollowSymlinks(final Converter<T> converter)
      throws InterruptedException, IOException {
    return new NoFollowWrapper<>(get(false, converter, Loggers.getLogger(), PATH_WATCHER_FACTORY));
  }

  static <T> FileTreeRepository<T> get(
      final boolean followLinks,
      final Converter<T> converter,
      final Logger logger,
      final IOFunction<Logger, PathWatcher<Event>> newPathWatcher)
      throws InterruptedException, IOException {
    try {
      final SymlinkWatcher symlinkWatcher =
          followLinks ? new SymlinkWatcher(newPathWatcher.apply(logger), logger) : null;
      final Executor callbackExecutor =
          Executor.make("FileTreeRepository-callback-executor", logger);
      final FileCacheDirectoryTree<T> tree =
          new FileCacheDirectoryTree<>(converter, callbackExecutor, symlinkWatcher, false, logger);
      final PathWatcher<Event> pathWatcher = newPathWatcher.apply(logger);
      pathWatcher.addObserver(fileTreeObserver(tree, logger));
      final FileCachePathWatcher<T> watcher = new FileCachePathWatcher<>(tree, pathWatcher);
      return new FileTreeRepositoryImpl<>(tree, watcher, logger);
    } catch (final Interrupted e) {
      throw e.cause;
    }
  }

  private static final IOFunction<Logger, PathWatcher<Event>> PATH_WATCHER_FACTORY =
      new IOFunction<Logger, PathWatcher<Event>>() {
        @Override
        public PathWatcher<Event> apply(final Logger logger) throws IOException {
          try {
            return PathWatchers.noFollowSymlinks(logger);
          } catch (final InterruptedException e) {
            throw new Interrupted(e);
          }
        }
      };

  private static class Interrupted extends RuntimeException {
    final InterruptedException cause;

    Interrupted(final InterruptedException cause) {
      this.cause = cause;
    }
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
    public List<Entry<T>> listEntries(
        final Path path, final int maxDepth, final Filter<? super Entry<T>> filter)
        throws IOException {
      return delegate.listEntries(path, maxDepth, filter);
    }

    @Override
    public List<TypedPath> list(
        final Path path, final int maxDepth, final Filter<? super TypedPath> filter)
        throws IOException {
      return delegate.list(path, maxDepth, filter);
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public int addObserver(final Observer<? super Entry<T>> observer) {
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
