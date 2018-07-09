// Do not edit this file manually. It is autogenerated.

package com.swoval.files

import com.swoval.functional.Consumer
import com.swoval.runtime.Platform
import java.io.IOException
import java.nio.file.Path
import scala.beans.{ BeanProperty, BooleanBeanProperty }

object PathWatchers {

  val DEFAULT_FACTORY: Factory = new Factory() {
    override def create(callback: Consumer[Event],
                        executor: Executor,
                        directoryRegistry: DirectoryRegistry): PathWatcher =
      get(callback, executor, directoryRegistry)
  }

  /**
   * Create a PathWatcher for the runtime platform.
   *
   * @param callback [[com.swoval.functional.Consumer]] to run on file events
   * @return PathWatcher for the runtime platform
   *     initialized
   */
  def get(callback: Consumer[Event]): PathWatcher =
    get(callback, Executor.make("com.swoval.files.PathWatcher-internal-executor"), null)

  /**
   * Create a PathWatcher for the runtime platform.
   *
   * @param callback [[Consumer]] to run on file events
   * @param executor provides a single threaded context to manage state
   * @return PathWatcher for the runtime platform
   *     initialized
   */
  def get(callback: Consumer[Event], executor: Executor): PathWatcher =
    get(callback, executor, null)

  /**
   * Create a PathWatcher for the runtime platform.
   *
   * @param callback [[Consumer]] to run on file events
   * @param executor provides a single threaded context to manage state
   * @param registry The registry of directories to monitor
   * @return PathWatcher for the runtime platform
   *     initialized
   */
  def get(callback: Consumer[Event], executor: Executor, registry: DirectoryRegistry): PathWatcher =
    if (Platform.isMac) new ApplePathWatcher(callback, executor, registry)
    else PlatformWatcher.make(callback, executor, registry)

  /**
   * Instantiates new [[PathWatcher]] instances with a [[Consumer]]. This is primarily so
   * that the [[PathWatcher]] in [[FileCache]] may be changed in testing.
   */
  abstract class Factory {

    /**
     * Creates a new PathWatcher.
     *
     * @param callback the callback to invoke on directory updates
     * @param executor the executor on which internal updates are invoked
     * @return a PathWatcher instance.
     *     can occur on mac.
     *     and windows.
     */
    def create(callback: Consumer[Event], executor: Executor): PathWatcher =
      create(callback, executor, null)

    /**
     * Creates a new PathWatcher.
     *
     * @param callback the callback to invoke on directory updates
     * @param executor the executor on which internal updates are invoked
     * @param directoryRegistry the registry of directories to monitor
     * @return A PathWatcher instance
     *     can occur on mac
     *     and windows
     */
    def create(callback: Consumer[Event],
               executor: Executor,
               directoryRegistry: DirectoryRegistry): PathWatcher

  }

  object Event {

    object Kind {

      /**
 A new file was created.
       */
      val Create: Kind = new Kind("Create", 1)

      /**
 The file was deleted.
       */
      val Delete: Kind = new Kind("Delete", 2)

      /**
 An error occurred processing the event.
       */
      val Error: Kind = new Kind("Error", 4)

      /**
 An existing file was modified.
       */
      val Modify: Kind = new Kind("Modify", 3)

      /**
 An overflow occurred in the underlying path monitor.
       */
      val Overflow: Kind = new Kind("Overflow", 0)

    }

    /**
     * An enum like class to indicate the type of file event. It isn't an actual enum because the
     * scala.js codegen has problems with enum types.
     */
    class Kind(private val name: String, private val priority: Int) extends Comparable[Kind] {

      override def toString(): String = name

      override def equals(other: Any): Boolean = other match {
        case other: Kind => other.name == this.name
        case _           => false

      }

      override def hashCode(): Int = name.hashCode

      override def compareTo(that: Kind): Int =
        java.lang.Integer.compare(this.priority, that.priority)

    }

  }

  /**
 Container for [[PathWatcher]] events
   */
  class Event(@BeanProperty val path: Path, @BeanProperty val kind: Event.Kind) {

    override def equals(other: Any): Boolean = other match {
      case other: Event => {
        val that: Event = other
        this.path == that.path && this.kind == that.kind
      }
      case _ => false

    }

    override def hashCode(): Int = path.hashCode ^ kind.hashCode

    override def toString(): String = "Event(" + path + ", " + kind + ")"

  }

}
