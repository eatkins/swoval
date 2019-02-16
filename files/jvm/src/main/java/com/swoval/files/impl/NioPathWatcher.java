package com.swoval.files.impl;

import static com.swoval.files.PathWatchers.Event.Kind.Create;
import static com.swoval.files.PathWatchers.Event.Kind.Delete;
import static com.swoval.files.PathWatchers.Event.Kind.Overflow;
import static java.util.Map.Entry;

import com.swoval.files.FileTreeDataViews;
import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.files.FileTreeView;
import com.swoval.files.FileTreeViews;
import com.swoval.files.FileTreeViews.Observer;
import com.swoval.files.PathWatcher;
import com.swoval.files.PathWatchers.Event;
import com.swoval.files.TypedPath;
import com.swoval.functional.Consumer;
import com.swoval.functional.Either;
import com.swoval.functional.Filter;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

class RootDirectories extends LockableMap<Path, CachedDirectory<WatchedDirectory>> {}
/** Provides a PathWatcher that is backed by a {@link java.nio.file.WatchService}. */
class NioPathWatcher implements PathWatcher<Event>, AutoCloseable {
  private final FileTreeView fileTreeView = FileTreeViews.noFollowSymlinks();
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Observers<Event> observers;
  private final RootDirectories rootDirectories = new RootDirectories();
  private final DirectoryRegistry directoryRegistry;
  private final Converter<WatchedDirectory> converter;
  private final Logger logger;

  private CacheObserver<WatchedDirectory> updateCacheObserver(final List<Event> events) {
    return new CacheObserver<WatchedDirectory>() {
      @Override
      @SuppressWarnings("EmptyCatchBlock")
      public void onCreate(final FileTreeDataViews.Entry<WatchedDirectory> newEntry) {
        events.add(new Event(newEntry.getTypedPath(), Create));
        try {
          final Iterator<TypedPath> it =
              fileTreeView
                  .list(
                      newEntry.getTypedPath().getPath(),
                      0,
                      new Filter<TypedPath>() {
                        @Override
                        public boolean accept(final TypedPath typedPath) {
                          return directoryRegistry.accept(typedPath.getPath());
                        }
                      })
                  .iterator();
          while (it.hasNext()) {
            final TypedPath tp = it.next();
            events.add(new Event(tp, Create));
          }
        } catch (final IOException e) {
          // This likely means the directory was deleted, which should be handle by the downstream
          // NioPathWatcherService.
        }
      }

      @Override
      public void onDelete(final FileTreeDataViews.Entry<WatchedDirectory> oldEntry) {
        if (oldEntry.getValue().isRight()) {
          if (Loggers.shouldLog(logger, Level.DEBUG))
            logger.debug(this + " closing key for " + oldEntry.getTypedPath().getPath());
          oldEntry.getValue().get().close();
        }
        events.add(new Event(oldEntry.getTypedPath(), Delete));
      }

      @Override
      public void onUpdate(
          final FileTreeDataViews.Entry<WatchedDirectory> oldEntry,
          final FileTreeDataViews.Entry<WatchedDirectory> newEntry) {}

      @Override
      public void onError(final IOException exception) {}
    };
  }

  private final NioPathWatcherService service;

  NioPathWatcher(
      final DirectoryRegistry directoryRegistry,
      final RegisterableWatchService watchService,
      final Logger logger)
      throws InterruptedException {
    this.directoryRegistry = directoryRegistry;
    this.logger = logger;
    this.observers = new Observers<>(logger);
    this.service =
        new NioPathWatcherService(
            new Consumer<Event>() {
              @Override
              public void accept(final Event event) {
                if (!closed.get()) {
                  if (event.getKind() != Overflow) {
                    handleEvent(event);
                  } else {
                    handleOverflow(event);
                  }
                }
              }
            },
            watchService,
            logger);
    this.converter =
        new Converter<WatchedDirectory>() {
          @Override
          public WatchedDirectory apply(final TypedPath typedPath) {
            return typedPath.isDirectory() && !typedPath.isSymbolicLink()
                ? Either.getOrElse(
                    service.register(typedPath.getPath()), WatchedDirectories.INVALID)
                : WatchedDirectories.INVALID;
          }
        };
  }

  /**
   * Similar to register, but tracks all of the new files found in the directory. It polls the
   * directory until the contents stop changing to ensure that a callback is fired for each path in
   * the newly created directory (up to the maxDepth). The assumption is that once the callback is
   * fired for the path, it is safe to assume that no event for a new file in the directory is
   * missed. Without the polling, it would be possible that a new file was created in the directory
   * before we registered it with the watch service. If this happened, then no callback would be
   * invoked for that file.
   *
   * @param typedPath The newly created directory to add
   */
  private void add(final TypedPath typedPath, final List<Event> events) throws IOException {
    if (directoryRegistry.acceptPrefix(typedPath.getPath())) {
      final CachedDirectory<WatchedDirectory> dir = getOrAdd(typedPath.getPath());
      if (dir != null) {
        dir.update(typedPath, true).observe(updateCacheObserver(events));
      }
    }
  }

  private void remove(final Path path, List<Event> events) {
    final CachedDirectory<WatchedDirectory> root = rootDirectories.remove(path);
    final CachedDirectory<WatchedDirectory> dir = root != null ? root : find(path);
    if (dir != null) remove(dir, path, events);
  }

  private void remove(
      final CachedDirectory<WatchedDirectory> cachedDirectory,
      final Path path,
      final List<Event> events) {
    final List<FileTreeDataViews.Entry<WatchedDirectory>> toCancel = cachedDirectory.remove(path);
    if (path == null || path == cachedDirectory.getPath()) toCancel.add(cachedDirectory.getEntry());
    final Iterator<FileTreeDataViews.Entry<WatchedDirectory>> it = toCancel.iterator();
    while (it.hasNext()) {
      final FileTreeDataViews.Entry<WatchedDirectory> entry = it.next();
      final Either<IOException, WatchedDirectory> either = entry.getValue();
      if (either.isRight()) {
        if (events != null) {
          final TypedPath typedPath =
              TypedPaths.get(
                  entry.getTypedPath().getPath(),
                  TypedPaths.getKind(entry.getTypedPath()) | Entries.NONEXISTENT);
          events.add(new Event(typedPath, Delete));
        }
        either.get().close();
      }
    }
  }

  @Override
  public Either<IOException, Boolean> register(final Path path, final int maxDepth) {
    if (Loggers.shouldLog(logger, Level.DEBUG))
      logger.debug(this + " registering " + path + " with max depth " + maxDepth);
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    final int existingMaxDepth = directoryRegistry.maxDepthFor(absolutePath);
    final TypedPath typedPath = TypedPaths.get(absolutePath);
    Path realPath;
    try {
      realPath = absolutePath.toRealPath();
    } catch (final IOException e) {
      realPath = absolutePath.toAbsolutePath();
    }
    Either<IOException, Boolean> result = Either.right(false);
    if (existingMaxDepth < maxDepth) {
      directoryRegistry.addDirectory(typedPath.getPath(), maxDepth);
    }
    final CachedDirectory<WatchedDirectory> dir = getOrAdd(realPath);

    if (dir != null) {
      result = Either.right(true);
      try {
        dir.update(typedPath, true);
      } catch (final IOException e) {
        result = Either.left(e);
      }
    }
    if (Loggers.shouldLog(logger, Level.DEBUG))
      logger.debug(this + " registered " + path + " with max depth " + maxDepth);
    return result;
  }

  private CachedDirectory<WatchedDirectory> find(final Path path) {
    assert (path != null);
    if (rootDirectories.lock()) {
      try {
        final Iterator<Entry<Path, CachedDirectory<WatchedDirectory>>> it =
            rootDirectories.iterator();
        CachedDirectory<WatchedDirectory> result = null;
        while (result == null && it.hasNext()) {
          final Entry<Path, CachedDirectory<WatchedDirectory>> entry = it.next();
          final Path root = entry.getKey();
          if (path.startsWith(root)) {
            result = entry.getValue();
          }
        }
        return result;
      } finally {
        rootDirectories.unlock();
      }
    } else {
      return null;
    }
  }

  private CachedDirectory<WatchedDirectory> getOrAdd(final Path path) {
    CachedDirectory<WatchedDirectory> result = null;
    if (rootDirectories.lock()) {
      try {
        if (!closed.get()) {
          result = find(path);
          if (result == null) {
            /*
             * We want to monitor the parent in case the file is deleted.
             */
            Path parent = path.getParent();
            boolean init = false;
            while (!init && parent != null) {
              try {
                result =
                    new CachedDirectoryImpl<>(
                            TypedPaths.get(parent),
                            converter,
                            Integer.MAX_VALUE,
                            new Filter<TypedPath>() {
                              @Override
                              public boolean accept(final TypedPath typedPath) {
                                return typedPath.isDirectory()
                                    && !typedPath.isSymbolicLink()
                                    && directoryRegistry.acceptPrefix(typedPath.getPath());
                              }
                            },
                            false)
                        .init();
                init = true;
                rootDirectories.put(parent, result);
              } catch (final IOException e) {
                parent = parent.getParent();
              }
            }
          }
        }
        return result;
      } finally {
        rootDirectories.unlock();
      }
    } else {
      return result;
    }
  }

  @Override
  @SuppressWarnings("EmptyCatchBlock")
  public void unregister(final Path path) {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    directoryRegistry.removeDirectory(absolutePath);
    if (rootDirectories.lock()) {
      try {
        final CachedDirectory<WatchedDirectory> dir = find(absolutePath);
        if (dir != null) {
          remove(dir, path, null);
          rootDirectories.remove(dir.getPath());
        }
      } finally {
        rootDirectories.unlock();
      }
    }
    if (Loggers.shouldLog(logger, Level.DEBUG)) logger.debug(this + " unregistered " + path);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      service.close();
      rootDirectories.clear();
    }
  }

  private void handleOverflow(final Event event) {
    final Path path = event.getTypedPath().getPath();
    if (Loggers.shouldLog(logger, Level.DEBUG))
      logger.debug(this + " received overflow for " + path);
    final List<Event> events = new ArrayList<>();
    if (rootDirectories.lock()) {
      try {
        final CachedDirectory<WatchedDirectory> root = find(path);
        if (root != null) {
          try {
            final Iterator<TypedPath> it =
                fileTreeView
                    .list(
                        path,
                        0,
                        new Filter<TypedPath>() {
                          @Override
                          public boolean accept(TypedPath typedPath) {
                            return typedPath.isDirectory()
                                && directoryRegistry.acceptPrefix(typedPath.getPath());
                          }
                        })
                    .iterator();
            while (it.hasNext()) {
              final TypedPath file = it.next();
              add(file, events);
            }
          } catch (final IOException e) {
            final List<FileTreeDataViews.Entry<WatchedDirectory>> removed = root.remove(path);
            final Iterator<FileTreeDataViews.Entry<WatchedDirectory>> removedIt =
                removed.iterator();
            while (removedIt.hasNext()) {
              events.add(
                  new Event(Entries.setExists(removedIt.next(), false).getTypedPath(), Delete));
            }
          }
        }
      } finally {
        rootDirectories.unlock();
      }
    }
    final TypedPath tp = TypedPaths.get(path);
    events.add(new Event(tp, tp.exists() ? Overflow : Delete));
    runCallbacks(events);
  }

  private void runCallbacks(final List<Event> events) {
    final Iterator<Event> it = events.iterator();
    final Set<Path> handled = new HashSet<>();
    while (it.hasNext()) {
      final Event event = it.next();
      final Path path = event.getTypedPath().getPath();
      if (directoryRegistry.accept(path) && handled.add(path)) {
        observers.onNext(new Event(TypedPaths.get(path), event.getKind()));
      }
    }
  }

  private void handleEvent(final Event event) {
    if (Loggers.shouldLog(logger, Level.DEBUG)) logger.debug(this + " received event " + event);
    final List<Event> events = new ArrayList<>();
    if (!closed.get() && rootDirectories.lock()) {
      try {
        if (directoryRegistry.acceptPrefix(event.getTypedPath().getPath())) {
          final boolean isDelete = event.getKind() == Delete;
          final Path path = event.getTypedPath().getPath();
          final TypedPath typedPath = TypedPaths.get(path);
          if (isDelete) remove(path, events);
          if (typedPath.exists()) {
            if (typedPath.isDirectory() && !typedPath.isSymbolicLink()) {
              if (Loggers.shouldLog(logger, Level.DEBUG))
                logger.debug(this + " adding directory for " + typedPath);
              try {
                add(typedPath, events);
              } catch (final IOException e) {
                remove(path, events);
              }
            }
          } else if (!isDelete) remove(path, events);
        }
        events.add(event);
      } finally {
        rootDirectories.unlock();
      }
    }
    if (Loggers.shouldLog(logger, Level.DEBUG)) {
      logger.debug(this + " generated " + events.toString() + " from initial event " + event);
    }
    runCallbacks(events);
  }

  @Override
  public int addObserver(final Observer<? super Event> observer) {
    return observers.addObserver(observer);
  }

  @Override
  public void removeObserver(final int handle) {
    observers.removeObserver(handle);
  }
}
