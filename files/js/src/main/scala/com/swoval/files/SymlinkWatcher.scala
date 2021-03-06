// Do not edit this file manually. It is autogenerated.

package com.swoval.files

import com.swoval.functional.Either.getOrElse
import com.swoval.functional.Either.leftProjection
import java.util.Map.Entry
import com.swoval.files.FileTreeViews.Observable
import com.swoval.files.FileTreeViews.Observer
import com.swoval.files.PathWatchers.Event
import com.swoval.files.PathWatchers.Event.Kind
import com.swoval.files.SymlinkWatcher.RegisteredPath
import com.swoval.functional.Either
import com.swoval.logging.Logger
import com.swoval.logging.Loggers
import com.swoval.logging.Loggers.Level
import java.io.IOException
import java.nio.file.FileSystemLoopException
import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import java.util.Iterator
import java.util.List
import java.util.Set
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import SymlinkWatcher._

class RegisteredPaths(reentrantLock: ReentrantLock)
    extends LockableMap[Path, RegisteredPath](new HashMap[Path, RegisteredPath](), reentrantLock)

object SymlinkWatcher {

  class RegisteredPath(val path: Path, base: Path) extends AutoCloseable {

    val paths: Set[Path] = new HashSet()

    paths.add(base)

    override def close(): Unit = {
      paths.clear()
    }

  }

}

/**
 * Monitors symlink targets. The [[SymlinkWatcher]] maintains a mapping of symlink targets to
 * symlink. When the symlink target is modified, the watcher will detect the update and invoke a
 * provided [[com.swoval.functional.Consumer]] for the symlink.
 */
class SymlinkWatcher(
    private val watcher: PathWatcher[PathWatchers.Event],
    private val logger: Logger
) extends Observable[Event]
    with AutoCloseable {

  private val isClosed: AtomicBoolean = new AtomicBoolean(false)

  private val observers: Observers[Event] = new Observers()

  private val callbackExecutor: Executor =
    Executor.make("com.swoval.files.SymlinkWather.callback-executor")

  val reentrantLock: ReentrantLock = new ReentrantLock()

  watcher.addObserver(new Observer[Event]() {
    override def onError(t: Throwable): Unit = {}

    override def onNext(event: Event): Unit = {
      if (Loggers.shouldLog(logger, Level.DEBUG))
        logger.debug(this + " received event " + event)
      if (!isClosed.get) {
        val paths: List[Path] = new ArrayList[Path]()
        val path: Path = event.getTypedPath.getPath
        val kind: Kind = event.getKind
        if (watchedSymlinksByTarget.lock()) {
          try {
            val registeredPath: RegisteredPath =
              find(path, watchedSymlinksByTarget)
            if (registeredPath != null) {
              val relativized: Path = registeredPath.path.relativize(path)
              val it: Iterator[Path] = registeredPath.paths.iterator()
              while (it.hasNext) {
                val rawPath: Path = it.next().resolve(relativized)
                if (!hasLoop(rawPath)) paths.add(rawPath)
              }
            }
          } finally watchedSymlinksByTarget.unlock()
        }
        if (!Files.exists(path)) {
          if (watchedSymlinksByTarget.lock()) {
            try {
              val registeredPath: RegisteredPath =
                watchedSymlinksByTarget.remove(path)
              if (registeredPath != null) {
                registeredPath.paths.remove(path)
                if (registeredPath.paths.isEmpty) {
                  watcher.unregister(path)
                }
              }
            } finally watchedSymlinksByTarget.unlock()
          }
        }
        val it: Iterator[Path] = paths.iterator()
        while (it.hasNext) {
          val typedPath: TypedPath = TypedPaths.get(it.next())
          if (Loggers.shouldLog(logger, Level.DEBUG))
            logger.debug(
              "SymlinkWatcher evaluating callback for " + ("link " + typedPath + " to target " + path)
            )
          observers.onNext(new Event(typedPath, kind))
        }
      }
    }
  })

  def this(watcher: PathWatcher[PathWatchers.Event]) =
    this(watcher, Loggers.getLogger)

  private val watchedSymlinksByTarget: RegisteredPaths = new RegisteredPaths(reentrantLock)

  override def addObserver(observer: Observer[_ >: Event]): Int =
    observers.addObserver(observer)

  override def removeObserver(handle: Int): Unit = {
    observers.removeObserver(handle)
  }

  private def find(path: Path, registeredPaths: RegisteredPaths): RegisteredPath = {
    val result: RegisteredPath = registeredPaths.get(path)
    if (result != null) result
    else if (path == null || path.getNameCount == 0) null
    else {
      val parent: Path = path.getParent
      if (parent == null || parent.getNameCount == 0) null
      else find(parent, registeredPaths)
    }
  }

  private def hasLoop(path: Path): Boolean = {
    var result: Boolean = false
    val parent: Path = path.getParent
    try {
      val realPath: Path = parent.toRealPath()
      result = parent.startsWith(realPath) && parent != realPath
    } catch {
      case e: IOException => {}

    }
    result
  }

  override def close(): Unit = {
    if (isClosed.compareAndSet(false, true)) {
      val targetIt: Iterator[RegisteredPath] =
        watchedSymlinksByTarget.values.iterator()
      while (targetIt.hasNext) targetIt.next().paths.clear()
      watchedSymlinksByTarget.clear()
      watcher.close()
      callbackExecutor.close()
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
  def addSymlink(path: Path, maxDepth: Int): Unit = {
    if (!isClosed.get) {
      val realPath: Path = path.toRealPath()
      if (path.startsWith(realPath) && path != realPath) {
        throw new FileSystemLoopException(path.toString)
      } else {
        if (Loggers.shouldLog(logger, Level.DEBUG))
          logger.debug(
            this + " SymlinkWatcher adding link " + path + " with max depth " +
              maxDepth
          )
        if (watchedSymlinksByTarget.lock()) {
          try {
            val targetRegistrationPath: RegisteredPath =
              watchedSymlinksByTarget.get(realPath)
            if (targetRegistrationPath == null) {
              val result: Either[IOException, Boolean] =
                watcher.register(realPath, maxDepth)
              if (getOrElse(result, false)) {
                watchedSymlinksByTarget.put(realPath, new RegisteredPath(realPath, path))
              } else if (result.isLeft) {
                throw leftProjection(result).getValue
              }
            } else {
              targetRegistrationPath.paths.add(path)
            }
          } finally watchedSymlinksByTarget.unlock()
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
  def remove(path: Path): Unit = {
    if (!isClosed.get) {
      if (watchedSymlinksByTarget.lock()) {
        try {
          var target: Path = null
          val it: Iterator[Entry[Path, RegisteredPath]] =
            watchedSymlinksByTarget.iterator()
          while (it.hasNext && target == null) {
            val entry: Entry[Path, RegisteredPath] = it.next()
            if (entry.getValue.paths.remove(path)) {
              target = entry.getKey
            }
          }
          if (target != null) {
            val targetRegisteredPath: RegisteredPath =
              watchedSymlinksByTarget.get(target)
            if (targetRegisteredPath != null) {
              targetRegisteredPath.paths.remove(path)
              if (targetRegisteredPath.paths.isEmpty) {
                watchedSymlinksByTarget.remove(target)
              }
            }
          }
          if (Loggers.shouldLog(logger, Level.DEBUG))
            logger.debug(this + " stopped monitoring link " + path)
        } finally watchedSymlinksByTarget.unlock()
      }
    }
  }

}
