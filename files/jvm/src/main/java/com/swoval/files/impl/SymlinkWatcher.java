package com.swoval.files.impl;

import static com.swoval.functional.Either.getOrElse;
import static com.swoval.functional.Either.leftProjection;
import static java.util.Map.Entry;

import com.swoval.files.Observable;
import com.swoval.files.Observer;
import com.swoval.files.PathWatcher;
import com.swoval.files.PathWatchers.Event;
import com.swoval.files.PathWatchers.Event.Kind;
import com.swoval.files.TypedPath;
import com.swoval.files.impl.SymlinkWatcher.RegisteredPath;
import com.swoval.files.impl.functional.Consumer;
import com.swoval.functional.Either;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import java.io.IOException;
import java.nio.file.FileSystemLoopException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

class RegisteredPaths extends LockableMap<Path, RegisteredPath> {
  RegisteredPaths(final ReentrantLock reentrantLock) {
    super(new HashMap<Path, RegisteredPath>(), reentrantLock);
  }
}
/**
 * Monitors symlink targets. The {@link SymlinkWatcher} maintains a mapping of symlink targets to
 * symlink. When the symlink target is modified, the watcher will detect the update and invoke a
 * provided {@link Consumer} for the symlink.
 */
class SymlinkWatcher implements Observable<Event>, AutoCloseable {
  private final AtomicBoolean isClosed = new AtomicBoolean(false);
  private final Observers<Event> observers;
  private final Executor callbackExecutor;
  private final Logger logger;

  SymlinkWatcher(final PathWatcher<Event> watcher, final Logger logger) {
    this.watcher = watcher;
    this.logger = logger;
    this.observers = new Observers<>(logger);
    callbackExecutor = Executor.make("com.swoval.files.SymlinkWather.callback-executor", logger);
    final ReentrantLock reentrantLock = new ReentrantLock();
    watchedSymlinksByTarget = new RegisteredPaths(reentrantLock);
    watcher.addObserver(
        new Observer<Event>() {
          @Override
          public void onError(final Throwable t) {}

          @Override
          public void onNext(final Event event) {
            if (Loggers.shouldLog(logger, Level.DEBUG))
              logger.debug(this + " received event " + event);
            if (!isClosed.get()) {
              final List<Path> paths = new ArrayList<>();
              final Path path = event.getTypedPath().getPath();
              final Kind kind = event.getKind();
              if (watchedSymlinksByTarget.lock()) {
                try {
                  final RegisteredPath registeredPath = find(path, watchedSymlinksByTarget);
                  if (registeredPath != null) {
                    final Path relativized = registeredPath.path.relativize(path);
                    final Iterator<Path> it = registeredPath.paths.iterator();
                    while (it.hasNext()) {
                      final Path rawPath = it.next().resolve(relativized);
                      if (!hasLoop(rawPath)) paths.add(rawPath);
                    }
                  }
                } finally {
                  watchedSymlinksByTarget.unlock();
                }
              }
              if (!Files.exists(path)) {
                if (watchedSymlinksByTarget.lock()) {
                  try {
                    final RegisteredPath registeredPath = watchedSymlinksByTarget.remove(path);
                    if (registeredPath != null) {
                      registeredPath.paths.remove(path);
                      if (registeredPath.paths.isEmpty()) {
                        watcher.unregister(path);
                      }
                    }
                  } finally {
                    watchedSymlinksByTarget.unlock();
                  }
                }
              }

              final Iterator<Path> it = paths.iterator();
              while (it.hasNext()) {
                final TypedPath typedPath = TypedPaths.get(it.next());
                if (Loggers.shouldLog(logger, Level.DEBUG))
                  logger.debug(
                      "SymlinkWatcher evaluating callback for "
                          + ("link " + typedPath + " to target " + path));
                observers.onNext(new Event(typedPath, kind));
              }
            }
          }
        });
  }

  private final RegisteredPaths watchedSymlinksByTarget;

  @Override
  public int addObserver(final Observer<? super Event> observer) {
    return observers.addObserver(observer);
  }

  @Override
  public void removeObserver(int handle) {
    observers.removeObserver(handle);
  }

  static final class RegisteredPath implements AutoCloseable {
    public final Path path;
    public final Set<Path> paths = new HashSet<>();

    RegisteredPath(final Path path, final Path base) {
      this.path = path;
      paths.add(base);
    }

    @Override
    public void close() {
      paths.clear();
    }
  }

  private RegisteredPath find(final Path path, final RegisteredPaths registeredPaths) {
    final RegisteredPath result = registeredPaths.get(path);
    if (result != null) return result;
    else if (path == null || path.getNameCount() == 0) return null;
    else {
      final Path parent = path.getParent();
      if (parent == null || parent.getNameCount() == 0) return null;
      else return find(parent, registeredPaths);
    }
  }

  @SuppressWarnings("EmptyCatchBlock")
  private boolean hasLoop(final Path path) {
    boolean result = false;
    final Path parent = path.getParent();
    try {
      final Path realPath = parent.toRealPath();
      result = parent.startsWith(realPath) && !parent.equals(realPath);
    } catch (final IOException e) {
    }
    return result;
  }

  /*
   * This declaration must go below the constructor for javascript codegen.
   */
  private final PathWatcher<Event> watcher;

  @Override
  @SuppressWarnings("EmptyCatchBlock")
  public void close() {
    if (isClosed.compareAndSet(false, true)) {
      final Iterator<RegisteredPath> targetIt = watchedSymlinksByTarget.values().iterator();
      while (targetIt.hasNext()) {
        targetIt.next().paths.clear();
      }
      watchedSymlinksByTarget.clear();
      watcher.close();
      callbackExecutor.close();
    }
  }

  /**
   * Start monitoring a symlink. As long as the target exists, this method will check if the parent
   * directory of the target is being monitored. If the parent isn't being registered, we register
   * it with the watch service. We add the target symlink to the set of symlinks watched in the
   * parent directory. We also add the base symlink to the set of watched symlinks for this
   * particular target.
   *
   * @param path The symlink base file.
   */
  @SuppressWarnings("EmptyCatchBlock")
  void addSymlink(final Path path, final int maxDepth) throws IOException {
    if (!isClosed.get()) {
      final Path realPath = path.toRealPath();
      if (path.startsWith(realPath) && !path.equals(realPath)) {
        throw new FileSystemLoopException(path.toString());
      } else {
        if (Loggers.shouldLog(logger, Level.DEBUG))
          logger.debug(
              this + " SymlinkWatcher adding link " + path + " with max depth " + maxDepth);
        if (watchedSymlinksByTarget.lock()) {
          try {
            final RegisteredPath targetRegistrationPath = watchedSymlinksByTarget.get(realPath);
            if (targetRegistrationPath == null) {
              final Either<IOException, Boolean> result = watcher.register(realPath, maxDepth);
              if (getOrElse(result, false)) {
                watchedSymlinksByTarget.put(realPath, new RegisteredPath(realPath, path));
              } else if (result.isLeft()) {
                throw leftProjection(result).getValue();
              }
            } else {
              targetRegistrationPath.paths.add(path);
            }
          } finally {
            watchedSymlinksByTarget.unlock();
          }
        }
      }
    }
  }

  /**
   * Removes the symlink from monitoring. If there are no remaining targets in the parent directory,
   * then we remove the parent directory from monitoring.
   *
   * @param path The symlink base to stop monitoring
   */
  void remove(final Path path) {
    if (!isClosed.get()) {
      if (watchedSymlinksByTarget.lock()) {
        try {
          Path target = null;
          final Iterator<Entry<Path, RegisteredPath>> it = watchedSymlinksByTarget.iterator();
          while (it.hasNext() && target == null) {
            final Entry<Path, RegisteredPath> entry = it.next();
            if (entry.getValue().paths.remove(path)) {
              target = entry.getKey();
            }
          }
          if (target != null) {
            final RegisteredPath targetRegisteredPath = watchedSymlinksByTarget.get(target);
            if (targetRegisteredPath != null) {
              targetRegisteredPath.paths.remove(path);
              if (targetRegisteredPath.paths.isEmpty()) {
                watchedSymlinksByTarget.remove(target);
              }
            }
          }
          if (Loggers.shouldLog(logger, Level.DEBUG))
            logger.debug(this + " stopped monitoring link " + path);
        } finally {
          watchedSymlinksByTarget.unlock();
        }
      }
    }
  }
}
