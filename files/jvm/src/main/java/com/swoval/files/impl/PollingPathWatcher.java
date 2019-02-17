package com.swoval.files.impl;

import static com.swoval.functional.Filters.AllPass;

import com.swoval.files.FileTreeDataViews;
import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.functional.IOFunction;
import com.swoval.files.FileTreeViews;
import com.swoval.files.PathWatchers.Event;
import com.swoval.files.PathWatchers.Event.Kind;
import com.swoval.files.TypedPath;
import com.swoval.files.api.Observer;
import com.swoval.files.api.PathWatcher;
import com.swoval.files.impl.functional.EitherImpl;
import com.swoval.functional.Either;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

class PollingPathWatcher implements PathWatcher<Event> {
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final boolean followLinks;
  private final DirectoryRegistry registry = new DirectoryRegistryImpl();
  private final Observers<Event> observers;
  private Map<Path, FileTreeDataViews.Entry<Long>> oldEntries;
  private final PeriodicTask periodicTask;
  private final IOFunction<TypedPath, Long> converter;
  private final Logger logger;

  PollingPathWatcher(
      final IOFunction<TypedPath, Long> converter,
      final boolean followLinks,
      final long pollInterval,
      final TimeUnit timeUnit,
      final Logger logger)
      throws InterruptedException {
    this.logger = logger;
    this.converter = converter;
    this.followLinks = followLinks;
    this.observers = new Observers<>(logger);
    oldEntries = getEntries();
    periodicTask = new PeriodicTask(new PollingRunnable(), timeUnit.toMillis(pollInterval));
  }

  PollingPathWatcher(
      final long pollInterval,
      final TimeUnit timeUnit,
      final boolean followLinks,
      final Logger logger)
      throws InterruptedException {
    this(
        new IOFunction<TypedPath, Long>() {
          @Override
          public Long apply(final TypedPath typedPath) {
            try {
              return Files.getLastModifiedTime(typedPath.getPath()).toMillis();
            } catch (final Exception e) {
              return 0L;
            }
          }
        },
        followLinks,
        pollInterval,
        timeUnit,
        logger);
  }

  @Override
  public Either<IOException, Boolean> register(final Path path, final int maxDepth) {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    boolean result;
    final List<FileTreeDataViews.Entry<Long>> entries = getEntries(absolutePath, maxDepth);
    synchronized (this) {
      addAll(oldEntries, entries);
      result = registry.addDirectory(absolutePath, maxDepth);
    }
    return EitherImpl.right(result);
  }

  @Override
  public void unregister(final Path path) {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    registry.removeDirectory(absolutePath);
  }

  @Override
  public void close() {
    if (isClosed.compareAndSet(false, true)) {
      registry.close();
      try {
        periodicTask.close();
      } catch (final InterruptedException e) {
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          Loggers.logException(logger, e);
        }
      }
    }
  }

  @Override
  public int addObserver(Observer<? super Event> observer) {
    return observers.addObserver(observer);
  }

  @Override
  public void removeObserver(final int handle) {
    observers.removeObserver(handle);
  }

  private void addAll(
      final Map<Path, FileTreeDataViews.Entry<Long>> map,
      final List<FileTreeDataViews.Entry<Long>> list) {
    final Iterator<FileTreeDataViews.Entry<Long>> it = list.iterator();
    while (it.hasNext()) {
      final FileTreeDataViews.Entry<Long> entry = it.next();
      map.put(entry.getTypedPath().getPath(), entry);
    }
  }

  private List<FileTreeDataViews.Entry<Long>> getEntries(final Path path, final int maxDepth) {
    try {
      final CachedDirectory<Long> view =
          new CachedDirectoryImpl<>(
                  TypedPaths.get(path, Entries.UNKNOWN),
                  converter,
                  maxDepth,
                  AllPass,
                  followLinks,
                  FileTreeViews.followSymlinks())
              .init();
      final List<FileTreeDataViews.Entry<Long>> newEntries = view.list(maxDepth, AllPass);
      final List<FileTreeDataViews.Entry<Long>> pathEntry = view.list(-1, AllPass);
      if (pathEntry.size() == 1) newEntries.add(pathEntry.get(0));
      return newEntries;
    } catch (final NotDirectoryException e) {
      final List<FileTreeDataViews.Entry<Long>> result = new ArrayList<>();
      final TypedPath typedPath = TypedPaths.get(path);
      result.add(Entries.get(typedPath, converter, typedPath));
      return result;
    } catch (final IOException e) {
      return Collections.emptyList();
    }
  }

  private Map<Path, FileTreeDataViews.Entry<Long>> getEntries() {
    // I have to use putAll because scala.js doesn't handle new HashMap(registry.registered()).
    final Map<Path, Integer> map = new ConcurrentHashMap<>();
    synchronized (this) {
      map.putAll(registry.registered());
    }
    final Iterator<Entry<Path, Integer>> it = map.entrySet().iterator();
    final Map<Path, FileTreeDataViews.Entry<Long>> result = new ConcurrentHashMap<>();
    while (it.hasNext()) {
      final Entry<Path, Integer> entry = it.next();
      final List<FileTreeDataViews.Entry<Long>> entries =
          getEntries(entry.getKey(), entry.getValue());
      addAll(result, entries);
    }
    return result;
  }

  private class PollingRunnable implements Runnable {
    final CacheObserver<Long> cacheObserver =
        new CacheObserver<Long>() {
          @Override
          public void onCreate(final FileTreeDataViews.Entry<Long> newEntry) {
            observers.onNext(new Event(newEntry.getTypedPath(), Kind.Create));
          }

          @Override
          public void onDelete(final FileTreeDataViews.Entry<Long> oldEntry) {
            observers.onNext(new Event(oldEntry.getTypedPath(), Kind.Delete));
          }

          @Override
          public void onUpdate(
              final FileTreeDataViews.Entry<Long> oldEntry,
              FileTreeDataViews.Entry<Long> newEntry) {
            if (!oldEntry.getValue().equals(newEntry.getValue())) {
              observers.onNext(new Event(newEntry.getTypedPath(), Kind.Modify));
            }
          }

          @Override
          public void onError(final IOException exception) {
            observers.onError(exception);
          }
        };

    @Override
    public void run() {
      final Map<Path, FileTreeDataViews.Entry<Long>> newEntries = getEntries();
      MapOps.diffDirectoryEntries(oldEntries, newEntries, cacheObserver);
      synchronized (this) {
        oldEntries = newEntries;
      }
    }
  }
}
