// Do not edit this file manually. It is autogenerated.

package com.swoval.files

import com.swoval.files.FileTreeDataViews.CacheObserver
import com.swoval.files.FileTreeDataViews.Converter
import com.swoval.files.FileTreeDataViews.Converters
import com.swoval.files.FileTreeDataViews.Entry
import com.swoval.files.FileTreeViews.Observer
import com.swoval.files.PathWatchers.Event
import com.swoval.functional.Either
import com.swoval.functional.Filter
import com.swoval.functional.IOFunction
import com.swoval.logging.Logger
import com.swoval.logging.Loggers
import com.swoval.logging.Loggers.Level
import java.io.IOException
import java.nio.file.Path
import java.util.List

object FileTreeRepositories {

  /**
   * Create a default file tree repository. This method does not specify whether or not it follows
   * symlinks. If this matters prefer [[FileTreeRepositories.noFollowSymlinks]] or
   * [[FileTreeRepositories.followSymlinks]].
   *
   * @param converter converts a path to the cached value type T
   * @param logger the logger
   * @tparam T the value type of the cache entries
   * @return a file tree repository.
   */
  def getDefault[T <: AnyRef](converter: Converter[T], logger: Logger): FileTreeRepository[T] =
    impl(true, converter, logger, PATH_WATCHER_FACTORY)

  /**
   * Create a default file tree repository. This method does not specify whether or not it follows
   * symlinks. If this matters prefer [[FileTreeRepositories.noFollowSymlinks]] or
   * [[FileTreeRepositories.followSymlinks]].
   *
   * @param converter converts a path to the cached value type T
   * @tparam T the value type of the cache entries
   * @return a file tree repository.
   */
  def getDefault[T <: AnyRef](converter: Converter[T]): FileTreeRepository[T] =
    getDefault(converter, Loggers.getLogger)

  /**
   * Create a default file tree repository. This method does not specify whether or not it follows
   * symlinks. If this matters prefer [[FileTreeRepositories.noFollowSymlinks]] or
   * [[FileTreeRepositories.followSymlinks]].
   *
   * @return a file tree repository.
   */
  def getDefault(): FileTreeRepository[TypedPath] =
    getDefault(Converters.IDENTITY, Loggers.getLogger)

  /**
   * Create a file tree repository that follows symlinks.
   *
   * @param converter converts a path to the cached value type T
   * @param logger the logger
   * @tparam T the value type of the cache entries
   * @return a file tree repository.
   */
  def followSymlinks[T <: AnyRef](converter: Converter[T], logger: Logger): FollowSymlinks[T] =
    new FollowWrapper(impl(true, converter, logger, PATH_WATCHER_FACTORY))

  /**
   * Create a file tree repository that follows symlinks.
   *
   * @param converter converts a path to the cached value type T
   * @tparam T the value type of the cache entries
   * @return a file tree repository.
   */
  def followSymlinks[T <: AnyRef](converter: Converter[T]): FileTreeRepository[T] =
    followSymlinks(converter, Loggers.getLogger)

  /**
   * Create a file tree repository that follows symlinks.
   *
   * @return a file tree repository.
   */
  def followSymlinks(): FileTreeRepository[TypedPath] =
    followSymlinks(Converters.IDENTITY, Loggers.getLogger)

  /**
   * Create a default file tree repository. This method does not specify whether or not it follows
   * symlinks so if this matters prefer TODO link.
   *
   * @param converter converts a path to the cached value type T
   * @param logger the logger
   * @tparam T the value type of the cache entries
   * @return a file tree repository.
   */
  def noFollowSymlinks[T <: AnyRef](converter: Converter[T], logger: Logger): NoFollowSymlinks[T] =
    new NoFollowWrapper(impl(false, converter, logger, PATH_WATCHER_FACTORY))

  /**
   * Create a default file tree repository. This method does not specify whether or not it follows
   * symlinks so if this matters prefer TODO link.
   *
   * @param converter converts a path to the cached value type T
   * @tparam T the value type of the cache entries
   * @return a file tree repository.
   */
  def noFollowSymlinks[T <: AnyRef](converter: Converter[T]): NoFollowSymlinks[T] =
    noFollowSymlinks(converter, Loggers.getLogger)

  def impl[T <: AnyRef](
      followLinks: Boolean,
      converter: Converter[T],
      logger: Logger,
      newPathWatcher: IOFunction[Logger, PathWatcher[Event]]): FileTreeRepository[T] = {
    val symlinkWatcher: SymlinkWatcher =
      if (followLinks) new SymlinkWatcher(newPathWatcher.apply(logger), logger)
      else null
    val callbackExecutor: Executor =
      Executor.make("FileTreeRepository-callback-executor", logger)
    val tree: FileCacheDirectoryTree[T] =
      new FileCacheDirectoryTree[T](converter, callbackExecutor, symlinkWatcher, false, logger)
    val pathWatcher: PathWatcher[PathWatchers.Event] =
      newPathWatcher.apply(logger)
    pathWatcher.addObserver(fileTreeObserver(tree, logger))
    val watcher: FileCachePathWatcher[T] =
      new FileCachePathWatcher[T](tree, pathWatcher)
    new FollowWrapper[T](new FileTreeRepositoryImpl[T](tree, watcher))
  }

  private def fileTreeObserver(tree: FileCacheDirectoryTree[_], logger: Logger): Observer[Event] =
    new Observer[Event]() {
      override def onError(t: Throwable): Unit = {
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          logger.error("Error while monitoring the file system " + t)
        }
      }

      override def onNext(event: Event): Unit = {
        tree.handleEvent(event)
      }
    }

  private class Wrapper[T <: AnyRef](private val delegate: FileTreeRepository[T])
      extends FileTreeRepository[T] {

    override def register(path: Path, maxDepth: Int): Either[IOException, Boolean] =
      delegate.register(path, maxDepth)

    override def unregister(path: Path): Unit = {
      delegate.unregister(path)
    }

    override def listEntries(path: Path,
                             maxDepth: Int,
                             filter: Filter[_ >: Entry[T]]): List[Entry[T]] =
      delegate.listEntries(path, maxDepth, filter)

    override def list(path: Path, maxDepth: Int, filter: Filter[_ >: TypedPath]): List[TypedPath] =
      delegate.list(path, maxDepth, filter)

    override def close(): Unit = {
      delegate.close()
    }

    override def addObserver(observer: Observer[_ >: Entry[T]]): Int =
      delegate.addObserver(observer)

    override def removeObserver(handle: Int): Unit = {
      delegate.removeObserver(handle)
    }

    override def addCacheObserver(observer: CacheObserver[T]): Int =
      delegate.addCacheObserver(observer)

  }

  private class NoFollowWrapper[T <: AnyRef](delegate: FileTreeRepository[T])
      extends Wrapper[T](delegate)
      with NoFollowSymlinks[T] {

    override def toString(): String =
      "NoFollowSymlinksFileTreeRepository@" + System.identityHashCode(this)

  }

  private class FollowWrapper[T <: AnyRef](delegate: FileTreeRepository[T])
      extends Wrapper[T](delegate)
      with FollowSymlinks[T] {

    override def toString(): String =
      "SymlinkFollowingFileTreeRepository@" + System.identityHashCode(this)

  }

  private class Interrupted(val cause: InterruptedException) extends RuntimeException

  private val PATH_WATCHER_FACTORY: IOFunction[Logger, PathWatcher[Event]] =
    new IOFunction[Logger, PathWatcher[Event]]() {
      override def apply(logger: Logger): PathWatcher[Event] =
        PathWatchers.noFollowSymlinks(logger)
    }

  trait FollowSymlinks[T <: AnyRef] extends FileTreeRepository[T]

  trait NoFollowSymlinks[T <: AnyRef] extends FileTreeRepository[T]

}
