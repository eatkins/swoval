package com.swoval.files;

import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.files.FileTreeDataViews.Entry;
import com.swoval.files.FileTreeRepositories.FollowSymlinks;
import com.swoval.files.FileTreeRepositories.NoFollowSymlinks;
import com.swoval.files.FileTreeViews.Observer;
import com.swoval.files.PathWatchers.Event;
import com.swoval.functional.Either;
import com.swoval.functional.Filter;
import com.swoval.functional.IOFunction;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import com.swoval.runtime.ShutdownHooks;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;

class FileTreeRepositoryImpl<T> implements FileTreeRepository<T> {
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final FileCacheDirectoryTree<T> directoryTree;
  private final FileCachePathWatcher<T> watcher;
  private final Runnable closeRunnable =
      new Runnable() {
        @Override
        @SuppressWarnings("EmptyCatchBlock")
        public void run() {
          if (closed.compareAndSet(false, true)) {
            ShutdownHooks.removeHook(shutdownHookId);
            watcher.close();
            directoryTree.close();
          }
        }
      };
  private final int shutdownHookId;
  private final Logger logger;

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
      final PathWatcher<PathWatchers.Event> pathWatcher = newPathWatcher.apply(logger);
      pathWatcher.addObserver(fileTreeObserver(tree, logger));
      final FileCachePathWatcher<T> watcher = new FileCachePathWatcher<>(tree, pathWatcher);
      return new FileTreeRepositoryImpl<T>(tree, watcher);
    } catch (final Interrupted e) {
      throw e.cause;
    }
  }

  static final IOFunction<Logger, PathWatcher<Event>> PATH_WATCHER_FACTORY =
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

  FileTreeRepositoryImpl(
      final FileCacheDirectoryTree<T> directoryTree,
      final FileCachePathWatcher<T> watcher,
      final Logger logger) {
    this.shutdownHookId = ShutdownHooks.addHook(1, closeRunnable);
    this.directoryTree = directoryTree;
    this.watcher = watcher;
    this.logger = logger;
  }

  FileTreeRepositoryImpl(
      final FileCacheDirectoryTree<T> directoryTree, final FileCachePathWatcher<T> watcher) {
    this(directoryTree, watcher, Loggers.getLogger());
  }

  /** Cleans up the path watcher and clears the directory cache. */
  @Override
  public void close() {
    closeRunnable.run();
  }

  @Override
  public int addObserver(final Observer<? super FileTreeDataViews.Entry<T>> observer) {
    return addCacheObserver(
        new CacheObserver<T>() {
          @Override
          public void onCreate(final Entry<T> newEntry) {
            observer.onNext(newEntry);
          }

          @Override
          public void onDelete(final Entry<T> oldEntry) {
            observer.onNext(oldEntry);
          }

          @Override
          public void onUpdate(final Entry<T> oldEntry, final Entry<T> newEntry) {
            observer.onNext(newEntry);
          }

          @Override
          public void onError(final IOException exception) {
            observer.onError(exception);
          }
        });
  }

  @Override
  public void removeObserver(int handle) {
    directoryTree.removeObserver(handle);
  }

  @Override
  public List<FileTreeDataViews.Entry<T>> listEntries(
      final Path path,
      final int maxDepth,
      final Filter<? super FileTreeDataViews.Entry<T>> filter) {
    return directoryTree.listEntries(path, maxDepth, filter);
  }

  @Override
  public Either<IOException, Boolean> register(final Path path, final int maxDepth) {
    try {
      final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
      final Either<IOException, Boolean> res =
          Either.right(watcher.register(absolutePath, maxDepth));
      if (Loggers.shouldLog(logger, Level.DEBUG))
        logger.debug(this + " registered " + path + " with max depth " + maxDepth);
      return res;
    } catch (final IOException e) {
      return Either.left(e);
    }
  }

  @Override
  @SuppressWarnings("EmptyCatchBlock")
  public void unregister(final Path path) {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    watcher.unregister(absolutePath);
  }

  @Override
  public List<TypedPath> list(Path path, int maxDepth, Filter<? super TypedPath> filter) {
    return directoryTree.list(path, maxDepth, filter);
  }

  @Override
  public int addCacheObserver(final CacheObserver<T> observer) {
    return directoryTree.addCacheObserver(observer);
  }

  abstract static class Callback implements Runnable, Comparable<Callback> {
    private final Path path;

    Callback(final Path path) {
      this.path = path;
    }

    @Override
    public int compareTo(@NotNull final Callback that) {
      return this.path.compareTo(that.path);
    }
  }

  private static class Wrapper<T> implements FileTreeRepository<T> {
    private final FileTreeRepository<T> delegate;

    Wrapper(final FileTreeRepository<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Either<IOException, Boolean> register(Path path, int maxDepth) {
      return delegate.register(path, maxDepth);
    }

    @Override
    public void unregister(Path path) {
      delegate.unregister(path);
    }

    @Override
    public List<Entry<T>> listEntries(Path path, int maxDepth, Filter<? super Entry<T>> filter)
        throws IOException {
      return delegate.listEntries(path, maxDepth, filter);
    }

    @Override
    public List<TypedPath> list(Path path, int maxDepth, Filter<? super TypedPath> filter)
        throws IOException {
      return delegate.list(path, maxDepth, filter);
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public int addObserver(Observer<? super Entry<T>> observer) {
      return delegate.addObserver(observer);
    }

    @Override
    public void removeObserver(int handle) {
      delegate.removeObserver(handle);
    }

    @Override
    public int addCacheObserver(CacheObserver<T> observer) {
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
