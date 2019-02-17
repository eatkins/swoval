package com.swoval.files.impl;

import static com.swoval.files.PathWatchers.Event.Kind.Create;
import static com.swoval.files.PathWatchers.Event.Kind.Delete;
import static com.swoval.files.PathWatchers.Event.Kind.Error;
import static com.swoval.files.PathWatchers.Event.Kind.Modify;
import static com.swoval.files.PathWatchers.Event.Kind.Overflow;
import static com.swoval.functional.Filters.AllPass;

import com.swoval.files.CacheEntry;
import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.functional.IOFunction;
import com.swoval.files.FileTreeDataViews.ObservableCache;
import com.swoval.files.api.FileTreeView;
import com.swoval.files.api.Observer;
import com.swoval.files.api.PathWatcher;
import com.swoval.files.PathWatchers.Event;
import com.swoval.files.PathWatchers.Event.Kind;
import com.swoval.files.TypedPath;
import com.swoval.files.impl.FileTreeRepositoryImpl.Callback;
import com.swoval.functional.Filter;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import com.swoval.runtime.Platform;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

class FileCacheDirectories<T> extends LockableMap<Path, CachedDirectory<T>> {
  FileCacheDirectories(final ReentrantLock lock) {
    super(new HashMap<Path, CachedDirectory<T>>(), lock);
  }
}

class FileCachePendingFiles extends Lockable {
  private final Set<Path> pendingFiles = new HashSet<>();

  FileCachePendingFiles(final ReentrantLock reentrantLock) {
    super(reentrantLock);
  }

  void clear() {
    if (lock()) {
      try {
        pendingFiles.clear();
      } finally {
        unlock();
      }
    }
  }

  boolean add(final Path path) {
    if (lock()) {
      try {
        return pendingFiles.add(path);
      } finally {
        unlock();
      }
    } else {
      return false;
    }
  }

  boolean remove(final Path path) {
    if (lock()) {
      try {
        return pendingFiles.remove(path);
      } finally {
        unlock();
      }
    } else {
      return false;
    }
  }
}

class FileCacheDirectoryTree<T> implements ObservableCache<T>, FileTreeView<CacheEntry<T>> {
  private final DirectoryRegistry directoryRegistry = new DirectoryRegistryImpl();
  private final Filter<TypedPath> filter = DirectoryRegistries.toTypedPathFilter(directoryRegistry);
  private final IOFunction<TypedPath, T> converter;
  private final Executor callbackExecutor;
  private final boolean followLinks;
  private final boolean rescanOnDirectoryUpdate;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final Logger logger;
  private final CacheObservers<T> observers;
  final SymlinkWatcher symlinkWatcher;

  FileCacheDirectoryTree(
      final IOFunction<TypedPath, T> converter,
      final Executor callbackExecutor,
      final SymlinkWatcher symlinkWatcher,
      final boolean rescanOnDirectoryUpdate,
      final Logger logger) {
    this.converter = converter;
    this.callbackExecutor = callbackExecutor;
    this.symlinkWatcher = symlinkWatcher;
    this.followLinks = symlinkWatcher != null;
    this.rescanOnDirectoryUpdate = rescanOnDirectoryUpdate;
    this.logger = logger;
    observers = new CacheObservers<>(logger);
    if (symlinkWatcher != null) {
      final boolean log = System.getProperty("swoval.symlink.debug", "false").equals("true");
      symlinkWatcher.addObserver(
          new Observer<Event>() {
            @Override
            public void onError(final Throwable t) {
              if (log && Loggers.shouldLog(logger, Level.ERROR)) {
                Loggers.logException(logger, t);
              }
            }

            @Override
            public void onNext(final Event event) {
              handleEvent(event);
            }
          });
    }
    final ReentrantLock reentrantLock = new ReentrantLock();
    pendingFiles = new FileCachePendingFiles(reentrantLock);
    directories = new FileCacheDirectories<>(reentrantLock);
  }

  private final FileCacheDirectories<T> directories;
  private final FileCachePendingFiles pendingFiles;

  void unregister(final Path path) {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    if (directories.lock()) {
      try {
        directoryRegistry.removeDirectory(absolutePath);
        if (!directoryRegistry.accept(absolutePath)) {
          final CachedDirectory<T> dir = find(absolutePath);
          if (dir != null) {
            if (getPath(dir).equals(absolutePath)) {
              directories.remove(absolutePath);
            } else {
              dir.remove(absolutePath);
            }
          }
        }
      } finally {
        directories.unlock();
      }
    }
    if (Loggers.shouldLog(logger, Level.DEBUG)) logger.debug(this + " unregistered " + path);
  }

  private CachedDirectory<T> find(final Path path) {
    CachedDirectory<T> foundDir = null;
    final List<CachedDirectory<T>> dirs = directories.values();
    Collections.sort(
        dirs,
        new Comparator<CachedDirectory<T>>() {
          @Override
          public int compare(final CachedDirectory<T> left, final CachedDirectory<T> right) {
            // Descending order so that we find the most specific path
            return getPath(right).compareTo(getPath(left));
          }
        });
    final Iterator<CachedDirectory<T>> it = dirs.iterator();
    while (it.hasNext() && foundDir == null) {
      final CachedDirectory<T> dir = it.next();
      if (path.startsWith(getPath(dir))) {
        if (dir.getMaxDepth() == Integer.MAX_VALUE || path.equals(getPath(dir))) {
          foundDir = dir;
        } else {
          int depth = getPath(dir).relativize(path).getNameCount() - 1;
          if (depth <= dir.getMaxDepth()) {
            foundDir = dir;
          }
        }
      }
    }
    return foundDir;
  }

  private void runCallbacks(final List<Callback> callbacks) {
    if (!callbacks.isEmpty() && !closed.get()) {
      callbackExecutor.run(
          new Runnable() {
            @Override
            public void run() {
              Collections.sort(callbacks);
              final Iterator<Callback> it = callbacks.iterator();
              while (it.hasNext()) {
                final Callback callback = it.next();
                if (Loggers.shouldLog(logger, Level.DEBUG))
                  logger.debug(this + " running callback " + callback);
                try {
                  callback.run();
                } catch (final Exception e) {
                  if (Loggers.shouldLog(logger, Level.ERROR))
                    logger.error("Error running callback " + e);
                }
              }
            }
          });
    }
  }

  @SuppressWarnings("EmptyCatchBlock")
  void handleEvent(final Event event) {
    if (Loggers.shouldLog(logger, Level.DEBUG)) logger.debug(this + " received event " + event);
    final TypedPath typedPath = event.getTypedPath();
    final List<TypedPath> symlinks = new ArrayList<>();
    final List<Callback> callbacks = new ArrayList<>();
    if (!closed.get() && directories.lock()) {
      try {
        final Path path = typedPath.getPath();
        if (typedPath.exists()) {
          final CachedDirectory<T> dir = find(typedPath.getPath());
          if (dir != null) {
            try {
              final TypedPath updatePath =
                  (followLinks || !typedPath.isSymbolicLink())
                      ? typedPath
                      : TypedPaths.get(typedPath.getPath(), Entries.LINK);
              final boolean rescan = rescanOnDirectoryUpdate || event.getKind().equals(Overflow);
              if (Loggers.shouldLog(logger, Level.DEBUG))
                logger.debug(
                    (this + " updating " + updatePath.getPath())
                        + (" in " + dir.getEntry().getTypedPath()));
              dir.update(updatePath, rescan).observe(callbackObserver(callbacks, symlinks));
            } catch (final IOException e) {
              handleDelete(path, callbacks, symlinks);
            }
          } else if (pendingFiles.remove(path)) {
            if (Loggers.shouldLog(logger, Level.DEBUG))
              logger.debug(this + " found pending file for " + path);
            try {
              CachedDirectory<T> cachedDirectory;
              try {
                cachedDirectory = newCachedDirectory(path, directoryRegistry.maxDepthFor(path));
                if (Loggers.shouldLog(logger, Level.DEBUG))
                  logger.debug(this + " successfully initialiazed directory for " + path);
              } catch (final NotDirectoryException nde) {
                if (Loggers.shouldLog(logger, Level.DEBUG))
                  logger.debug(this + " unable to initialize directory for " + path);
                cachedDirectory = newCachedDirectory(path, -1);
              }
              final CachedDirectory<T> previous = directories.put(path, cachedDirectory);
              if (previous != null) previous.close();
              addCallback(
                  callbacks,
                  symlinks,
                  cachedDirectory.getEntry(),
                  null,
                  cachedDirectory.getEntry(),
                  Create,
                  null);
              final Iterator<CacheEntry<T>> it =
                  cachedDirectory.list(cachedDirectory.getMaxDepth(), AllPass).iterator();
              while (it.hasNext()) {
                final CacheEntry<T> cacheEntry = it.next();
                addCallback(callbacks, symlinks, cacheEntry, null, cacheEntry, Create, null);
              }
            } catch (final IOException e) {
              if (Loggers.shouldLog(logger, Level.ERROR)) {
                logger.error("Caught unexpected io exception handling event for " + path);
                Loggers.logException(logger, e);
              }
              pendingFiles.add(path);
            }
          }
        } else {
          if (Loggers.shouldLog(logger, Level.DEBUG))
            logger.debug(this + " deleting directory for " + path);
          handleDelete(path, callbacks, symlinks);
        }
      } finally {
        directories.unlock();
      }
      final Iterator<TypedPath> it = symlinks.iterator();
      while (it.hasNext()) {
        final TypedPath tp = it.next();
        final Path path = tp.getPath();
        if (symlinkWatcher != null) {
          if (tp.exists()) {
            try {
              symlinkWatcher.addSymlink(path, directoryRegistry.maxDepthFor(path));
            } catch (final IOException e) {
              observers.onError(e);
            }
          } else {
            symlinkWatcher.remove(path);
          }
        }
      }
      runCallbacks(callbacks);
    }
  }

  private void handleDelete(
      final Path path, final List<Callback> callbacks, final List<TypedPath> symlinks) {
    final List<Iterator<CacheEntry<T>>> removeIterators = new ArrayList<>();
    final Iterator<CachedDirectory<T>> directoryIterator =
        new ArrayList<>(directories.values()).iterator();
    while (directoryIterator.hasNext()) {
      final CachedDirectory<T> dir = directoryIterator.next();
      if (path.startsWith(getPath(dir))) {
        final List<CacheEntry<T>> updates =
            path.equals(getPath(dir))
                ? dir.list(Integer.MAX_VALUE, AllPass)
                : new ArrayList<CacheEntry<T>>();
        updates.addAll(dir.remove(path));
        final Iterator<Path> it = directoryRegistry.registered().keySet().iterator();
        while (it.hasNext()) {
          if (it.next().equals(path)) {
            pendingFiles.add(path);
          }
        }
        if (getPath(dir).equals(path)) {
          directories.remove(path);
          updates.add(dir.getEntry());
        }
        removeIterators.add(updates.iterator());
      }
    }
    final Iterator<Iterator<CacheEntry<T>>> it = removeIterators.iterator();
    while (it.hasNext()) {
      final Iterator<CacheEntry<T>> removeIterator = it.next();
      while (removeIterator.hasNext()) {
        final CacheEntry<T> cacheEntry = Entries.setExists(removeIterator.next(), false);
        if (symlinkWatcher != null && cacheEntry.getTypedPath().isSymbolicLink())
          symlinkWatcher.remove(cacheEntry.getTypedPath().getPath());
        addCallback(callbacks, symlinks, cacheEntry, cacheEntry, null, Delete, null);
      }
    }
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true) && directories.lock()) {
      try {
        callbackExecutor.close();
        if (symlinkWatcher != null) symlinkWatcher.close();
        directories.clear();
        observers.close();
        directoryRegistry.close();
        pendingFiles.clear();
      } finally {
        directories.unlock();
      }
    }
    if (Loggers.shouldLog(logger, Level.DEBUG)) logger.debug(this + " was closed");
  }

  CachedDirectory<T> register(final Path path, final int maxDepth, final PathWatcher<Event> watcher)
      throws IOException {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    if (directoryRegistry.addDirectory(absolutePath, maxDepth) && directories.lock()) {
      try {
        watcher.register(absolutePath, maxDepth);
        final List<CachedDirectory<T>> dirs = new ArrayList<>(directories.values());
        Collections.sort(
            dirs,
            new Comparator<CachedDirectory<T>>() {
              @Override
              public int compare(final CachedDirectory<T> left, final CachedDirectory<T> right) {
                return getPath(left).compareTo(getPath(right));
              }
            });
        final Iterator<CachedDirectory<T>> it = dirs.iterator();
        CachedDirectory<T> existing = null;
        while (it.hasNext() && existing == null) {
          final CachedDirectory<T> dir = it.next();
          if (absolutePath.startsWith(getPath(dir))) {
            final int depth = getPath(dir).relativize(absolutePath).getNameCount() - 1;
            if (dir.getMaxDepth() == Integer.MAX_VALUE || dir.getMaxDepth() - depth > maxDepth) {
              existing = dir;
            }
          }
        }
        CachedDirectory<T> dir;
        if (existing == null) {
          try {
            try {
              dir = newCachedDirectory(absolutePath, maxDepth);
            } catch (final NotDirectoryException e) {
              dir = newCachedDirectory(absolutePath, -1);
            }
            directories.put(absolutePath, dir);
          } catch (final NoSuchFileException e) {
            pendingFiles.add(absolutePath);
            dir = newCachedDirectory(absolutePath, -1);
          }
        } else {
          existing.update(TypedPaths.get(absolutePath));
          dir = existing;
        }
        cleanupDirectories(absolutePath, maxDepth);
        if (Loggers.shouldLog(logger, Level.DEBUG))
          logger.debug(this + " registered " + path + " with max depth " + maxDepth);
        return dir;
      } finally {
        directories.unlock();
      }
    } else {
      return null;
    }
  }

  private static Path getPath(final CachedDirectory<?> dir) {
    return dir.getEntry().getTypedPath().getPath();
  }

  private void cleanupDirectories(final Path path, final int maxDepth) {
    final Iterator<CachedDirectory<T>> it = directories.values().iterator();
    final List<Path> toRemove = new ArrayList<>();
    while (it.hasNext()) {
      final CachedDirectory<T> dir = it.next();
      final Path dirPath = getPath(dir);
      if (dirPath.startsWith(path) && !dirPath.equals(path)) {
        if (maxDepth == Integer.MAX_VALUE) {
          toRemove.add(dirPath);
        } else {
          int depth = path.relativize(dirPath).getNameCount();
          if (maxDepth - depth >= dir.getMaxDepth()) {
            toRemove.add(dirPath);
          }
        }
      }
    }
    final Iterator<Path> removeIterator = toRemove.iterator();
    while (removeIterator.hasNext()) {
      directories.remove(removeIterator.next());
    }
  }

  @SuppressWarnings("EmptyCatchBlock")
  private void addCallback(
      final List<Callback> callbacks,
      final List<TypedPath> symlinks,
      final CacheEntry<T> cacheEntry,
      final CacheEntry<T> oldCacheEntry,
      final CacheEntry<T> newCacheEntry,
      final Kind kind,
      final IOException ioException) {
    final TypedPath typedPath = cacheEntry == null ? null : cacheEntry.getTypedPath();
    if (typedPath != null && typedPath.isSymbolicLink() && followLinks) {
      symlinks.add(typedPath);
    }
    callbacks.add(
        new Callback(typedPath == null ? Paths.get("") : typedPath.getPath()) {
          @Override
          public void run() {
            try {
              if (ioException != null) {
                observers.onError(ioException);
              } else if (kind.equals(Create)) {
                observers.onCreate(newCacheEntry);
              } else if (kind.equals(Delete)) {
                observers.onDelete(Entries.setExists(oldCacheEntry, false));
              } else if (kind.equals(Modify)) {
                observers.onUpdate(oldCacheEntry, newCacheEntry);
              }
            } catch (final Exception e) {
              if (Loggers.shouldLog(logger, Level.ERROR)) {
                Loggers.logException(logger, e);
              }
            }
          }
        });
  }

  @Override
  public int addObserver(final Observer<? super CacheEntry<T>> observer) {
    return observers.addObserver(observer);
  }

  @Override
  public void removeObserver(final int handle) {
    observers.removeObserver(handle);
  }

  @Override
  public int addCacheObserver(final CacheObserver<T> observer) {
    return observers.addCacheObserver(observer);
  }

  @Override
  public List<CacheEntry<T>> list(
      final Path path, final int maxDepth, final Filter<? super CacheEntry<T>> filter) {
    if (directories.lock()) {
      try {
        final CachedDirectory<T> dir = find(path);
        if (dir == null) {
          return Collections.emptyList();
        } else if (dir.getEntry().getTypedPath().getPath().equals(path) && maxDepth == -1) {
          List<CacheEntry<T>> result = new ArrayList<>();
          result.add(dir.getEntry());
          return result;
        } else {
          final int depth = directoryRegistry.maxDepthFor(path);
          return dir.list(path, depth < maxDepth ? depth : maxDepth, filter);
        }
      } finally {
        directories.unlock();
      }
    } else {
      return Collections.emptyList();
    }
  }

  private CacheObserver<T> callbackObserver(
      final List<Callback> callbacks, final List<TypedPath> symlinks) {
    return new CacheObserver<T>() {
      @Override
      public void onCreate(final CacheEntry<T> newCacheEntry) {
        addCallback(callbacks, symlinks, newCacheEntry, null, newCacheEntry, Create, null);
      }

      @Override
      public void onDelete(final CacheEntry<T> oldCacheEntry) {
        addCallback(callbacks, symlinks, oldCacheEntry, oldCacheEntry, null, Delete, null);
      }

      @Override
      public void onUpdate(final CacheEntry<T> oldCacheEntry, final CacheEntry<T> newCacheEntry) {
        addCallback(callbacks, symlinks, oldCacheEntry, oldCacheEntry, newCacheEntry, Modify, null);
      }

      @Override
      public void onError(final IOException exception) {
        addCallback(callbacks, symlinks, null, null, null, Error, exception);
      }
    };
  }

  private CachedDirectory<T> newCachedDirectory(final Path path, final int depth)
      throws IOException {
    int attempt = 1;
    int MAX_ATTEMPTS = 3;
    CachedDirectory<T> result = null;
    do {
      try {
        result = new CachedDirectoryImpl<>(path, converter, depth, filter, followLinks).init();
      } catch (final AccessDeniedException e) {
        if (Platform.isWin()) {
          try {
            Sleep.sleep(0);
          } catch (final InterruptedException ie) {
            throw e;
          }
        }
      }
      attempt += 1;
    } while (result == null && attempt <= MAX_ATTEMPTS);
    if (result == null) throw new NoSuchFileException(path.toString());
    return result;
  }
}
