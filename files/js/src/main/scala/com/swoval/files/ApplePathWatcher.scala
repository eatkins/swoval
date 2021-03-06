// Do not edit this file manually. It is autogenerated.

package com.swoval.files

import com.swoval.files.PathWatchers.Event.Kind.Create
import com.swoval.files.PathWatchers.Event.Kind.Delete
import com.swoval.files.PathWatchers.Event.Kind.Modify
import com.swoval.files.FileTreeViews.Observer
import com.swoval.files.PathWatchers.Event
import com.swoval.files.apple.ClosedFileEventMonitorException
import com.swoval.files.apple.FileEvent
import com.swoval.files.apple.FileEventMonitor
import com.swoval.files.apple.FileEventMonitors
import com.swoval.files.apple.FileEventMonitors.Handle
import com.swoval.files.apple.FileEventMonitors.Handles
import com.swoval.files.apple.Flags
import com.swoval.functional.Consumer
import com.swoval.functional.Either
import com.swoval.logging.Logger
import com.swoval.logging.Loggers
import com.swoval.logging.Loggers.Level
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.ArrayList
import java.util.Iterator
import java.util.List
import java.util.Map.Entry
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import ApplePathWatcher._

class AppleFileEventStreams extends LockableMap[Path, ApplePathWatcher.Stream]

object ApplePathWatcher {

  class Stream(val fileEventMonitor: FileEventMonitor, val handle: Handle) extends AutoCloseable {

    override def close(): Unit = {
      if (handle != Handles.INVALID) {
        fileEventMonitor.stopStream(handle)
      }
    }

  }

  private val DefaultOnStreamRemoved: DefaultOnStreamRemoved =
    new DefaultOnStreamRemoved()

  /**
   * A no-op callback to invoke when appleFileEventStreams are removed.
   */
  class DefaultOnStreamRemoved() extends Consumer[String] {

    override def accept(stream: String): Unit = {}

  }

}

/**
 * Implements the PathWatcher for Mac OSX using the [[https://developer.apple.com/library/content/documentation/Darwin/Conceptual/FSEvents_ProgGuide/UsingtheFSEventsFramework/UsingtheFSEventsFramework.html Apple File System Events Api]].
 */
class ApplePathWatcher(
    private val latency: java.lang.Long,
    private val timeUnit: TimeUnit,
    private val flags: Flags.Create,
    onStreamRemoved: Consumer[String],
    managedDirectoryRegistry: DirectoryRegistry,
    private val logger: Logger
) extends PathWatcher[PathWatchers.Event] {

  private val directoryRegistry: DirectoryRegistry =
    if (managedDirectoryRegistry == null) new DirectoryRegistryImpl()
    else managedDirectoryRegistry

  private val closed: AtomicBoolean = new AtomicBoolean(false)

  private val appleFileEventStreams: AppleFileEventStreams =
    new AppleFileEventStreams()

  private val fileEventMonitor: FileEventMonitor = FileEventMonitors.get(
    new Consumer[FileEvent]() {
      override def accept(fileEvent: FileEvent): Unit = {
        if (Loggers.shouldLog(logger, Level.DEBUG))
          logger.debug(this + " received event for " + fileEvent.fileName)
        if (!closed.get) {
          val fileName: String = fileEvent.fileName
          val path: TypedPath = TypedPaths.get(Paths.get(fileName))
          if (directoryRegistry.accept(path.getPath)) {
            var event: Event = null
            event =
              if (fileEvent.itemIsFile())
                if (fileEvent.isNewFile && path.exists())
                  new Event(path, Create)
                else if (fileEvent.isRemoved || !path.exists())
                  new Event(path, Delete)
                else new Event(path, Modify)
              else if (path.exists()) new Event(path, Modify)
              else new Event(path, Delete)
            try {
              if (Loggers.shouldLog(logger, Level.DEBUG))
                logger.debug(this + " passing " + event + " to observers")
              observers.onNext(event)
            } catch {
              case e: Exception => {
                logger.debug(this + " invoking onError for " + e)
                observers.onError(e)
              }

            }
          }
        }
      }
    },
    new Consumer[String]() {
      override def accept(stream: String): Unit = {
        if (!closed.get) {
          appleFileEventStreams.remove(Paths.get(stream))
          onStreamRemoved.accept(stream)
        }
      }
    }
  )

  private val observers: Observers[PathWatchers.Event] = new Observers()

  override def addObserver(observer: Observer[_ >: Event]): Int =
    observers.addObserver(observer)

  override def removeObserver(handle: Int): Unit = {
    observers.removeObserver(handle)
  }

  /**
   * Registers a path
   *
   * @param path The directory to watch for file events
   * @param maxDepth The maximum number of subdirectory levels to visit
   * @return an [[com.swoval.functional.Either]] containing the result of the registration or an
   *     IOException if registration fails. This method should be idempotent and return true the
   *     first time the directory is registered or when the depth is changed. Otherwise it should
   *     return false.
   */
  override def register(path: Path, maxDepth: Int): Either[IOException, Boolean] = {
    val absolutePath: Path =
      if (path.isAbsolute) path else path.toAbsolutePath()
    register(absolutePath, flags, maxDepth)
  }

  /**
   * Registers with additional flags
   *
   * @param path The directory to watch for file events
   * @param flags The flags [[com.swoval.files.apple.Flags.Create]] to set for the directory
   * @param maxDepth The maximum number of subdirectory levels to visit
   * @return an [[com.swoval.functional.Either]] containing the result of the registration or an
   *     IOException if registration fails. This method should be idempotent and return true the
   *     first time the directory is registered or when the depth is changed. Otherwise it should
   *     return false.
   */
  def register(path: Path, flags: Flags.Create, maxDepth: Int): Either[IOException, Boolean] = {
    var result: Boolean = true
    val entry: Entry[Path, Stream] = find(path)
    directoryRegistry.addDirectory(path, maxDepth)
    if (entry == null) {
      try {
        val id: FileEventMonitors.Handle =
          fileEventMonitor.createStream(path, latency, timeUnit, flags)
        if (id == Handles.INVALID) {
          result = false
        } else {
          removeRedundantStreams(path)
          appleFileEventStreams.put(path, new Stream(fileEventMonitor, id))
        }
      } catch {
        case e: ClosedFileEventMonitorException => {
          close()
          result = false
        }

      }
    }
    if (Loggers.shouldLog(logger, Level.DEBUG))
      logger.debug(this + " registered " + path + " with max depth " + maxDepth)
    Either.right(result)
  }

  private def removeRedundantStreams(path: Path): Unit = {
    val toRemove: List[Path] = new ArrayList[Path]()
    if (appleFileEventStreams.lock()) {
      try {
        val it: Iterator[Entry[Path, Stream]] =
          appleFileEventStreams.iterator()
        while (it.hasNext) {
          val e: Entry[Path, Stream] = it.next()
          val key: Path = e.getKey
          if (key.startsWith(path) && key != path) {
            toRemove.add(key)
          }
        }
        val pathIterator: Iterator[Path] = toRemove.iterator()
        while (pathIterator.hasNext) unregister(pathIterator.next())
      } finally appleFileEventStreams.unlock()
    }
  }

  /**
   * Unregisters a path
   *
   * @param path The directory to remove from monitoring
   */
  override def unregister(path: Path): Unit = {
    val absolutePath: Path =
      if (path.isAbsolute) path else path.toAbsolutePath()
    if (!closed.get) {
      directoryRegistry.removeDirectory(absolutePath)
      val stream: Stream = appleFileEventStreams.remove(absolutePath)
      if (stream != null && stream.handle != Handles.INVALID) {
        try stream.close()
        catch {
          case e: ClosedFileEventMonitorException =>
            e.printStackTrace(System.err)

        }
      }
      if (Loggers.shouldLog(logger, Level.DEBUG))
        logger.debug("ApplePathWatcher unregistered " + path)
    }
  }

  /**
   * Stops all appleFileEventStreams and closes the FileEventsApi
   */
  override def close(): Unit = {
    if (closed.compareAndSet(false, true)) {
      if (Loggers.shouldLog(logger, Level.DEBUG))
        logger.debug(this + " closed")
      appleFileEventStreams.clear()
      fileEventMonitor.close()
    }
  }

  def this(directoryRegistry: DirectoryRegistry, logger: Logger) =
    this(
      10,
      TimeUnit.MILLISECONDS,
      new Flags.Create().setNoDefer().setFileEvents(),
      DefaultOnStreamRemoved,
      directoryRegistry,
      logger
    )

  /**
   * Creates a new ApplePathWatcher which is a wrapper around [[FileEventMonitor]], which in
   * turn is a native wrapper around [[https://developer.apple.com/library/content/documentation/Darwin/Conceptual/FSEvents_ProgGuide/Introduction/Introduction.html#//apple_ref/doc/uid/TP40005289-CH1-SW1
   * Apple File System Events]]
   *
   * @param latency specified in fractional seconds
   * @param flags Native flags
   * @param onStreamRemoved [[com.swoval.functional.Consumer]] to run when a redundant stream is
   *     removed from the underlying native file events implementation
   * @param managedDirectoryRegistry The nullable registry of directories to monitor. If this is
   *     non-null, then registrations are handled by an outer class and this watcher should not call
   *     add or remove directory.
   *     initialization
   */
  def this(
      latency: java.lang.Long,
      timeUnit: TimeUnit,
      flags: Flags.Create,
      onStreamRemoved: Consumer[String],
      managedDirectoryRegistry: DirectoryRegistry
  ) =
    this(latency, timeUnit, flags, onStreamRemoved, managedDirectoryRegistry, Loggers.getLogger)

  private def find(path: Path): Entry[Path, Stream] = {
    val it: Iterator[Entry[Path, Stream]] = appleFileEventStreams.iterator()
    var result: Entry[Path, Stream] = null
    while (result == null && it.hasNext) {
      val entry: Entry[Path, Stream] = it.next()
      if (path.startsWith(entry.getKey)) {
        result = entry
      }
    }
    result
  }

}

object ApplePathWatchers {

  def get(
      followLinks: Boolean,
      directoryRegistry: DirectoryRegistry
  ): PathWatcher[PathWatchers.Event] =
    get(followLinks, directoryRegistry, Loggers.getLogger)

  def get(
      followLinks: Boolean,
      directoryRegistry: DirectoryRegistry,
      logger: Logger
  ): PathWatcher[PathWatchers.Event] = {
    val pathWatcher: ApplePathWatcher =
      new ApplePathWatcher(directoryRegistry, logger)
    if (followLinks)
      new SymlinkFollowingPathWatcher(pathWatcher, directoryRegistry, logger)
    else pathWatcher
  }

}
