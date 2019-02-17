package com.swoval.files.impl;

import com.swoval.files.FileTreeRepository;
import com.swoval.files.api.Observer;
import com.swoval.files.cache.Entry;
import com.swoval.files.cache.Event;
import com.swoval.files.impl.functional.EitherImpl;
import com.swoval.functional.Either;
import com.swoval.functional.Filter;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import com.swoval.runtime.ShutdownHooks;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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

  FileTreeRepositoryImpl(
      final FileCacheDirectoryTree<T> directoryTree,
      final FileCachePathWatcher<T> watcher,
      final Logger logger) {
    this.shutdownHookId = ShutdownHooks.addHook(1, closeRunnable);
    this.directoryTree = directoryTree;
    this.watcher = watcher;
    this.logger = logger;
  }

  /** Cleans up the path watcher and clears the directory cache. */
  @Override
  public void close() {
    closeRunnable.run();
  }

  @Override
  public int addObserver(Observer<? super Event<T>> observer) {
    return directoryTree.addObserver(observer);
  }

  @Override
  public void removeObserver(int handle) {
    directoryTree.removeObserver(handle);
  }

  @Override
  public List<Entry<T>> list(
      final Path path, final int maxDepth, final Filter<? super Entry<T>> filter) {
    return directoryTree.list(path, maxDepth, filter);
  }

  @Override
  public Either<IOException, Boolean> register(final Path path, final int maxDepth) {
    try {
      final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
      final Either<IOException, Boolean> res =
          EitherImpl.right(watcher.register(absolutePath, maxDepth));
      if (Loggers.shouldLog(logger, Level.DEBUG))
        logger.debug(this + " registered " + path + " with max depth " + maxDepth);
      return res;
    } catch (final IOException e) {
      return EitherImpl.left(e);
    }
  }

  @Override
  @SuppressWarnings("EmptyCatchBlock")
  public void unregister(final Path path) {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    watcher.unregister(absolutePath);
  }

  abstract static class Callback implements Runnable, Comparable<Callback> {
    private final Path path;

    Callback(final Path path) {
      this.path = path;
    }

    @Override
    public int compareTo(final Callback that) {
      return this.path.compareTo(that.path);
    }
  }
}
