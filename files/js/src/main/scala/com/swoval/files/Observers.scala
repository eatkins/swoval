// Do not edit this file manually. It is autogenerated.

package com.swoval.files

import com.swoval.files.FileTreeDataViews.CacheObserver
import com.swoval.files.FileTreeViews.Observer
import com.swoval.logging.Logger
import com.swoval.logging.Loggers
import com.swoval.logging.Loggers.Level
import java.util.ArrayList
import java.util.Iterator
import java.util.LinkedHashMap
import java.util.List
import java.util.Map
import java.util.concurrent.atomic.AtomicInteger

/**
 * Container class that wraps multiple [[FileTreeViews.Observer]] and runs the callbacks for
 * each whenever the [[PathWatcher]] detects an event.
 *
 * @tparam T the data type for the [[PathWatcher]] to which the observers correspond
 */
class Observers[T](private val logger: Logger)
    extends FileTreeViews.Observer[T]
    with AutoCloseable {

  private val counter: AtomicInteger = new AtomicInteger(0)

  private val observers: Map[Integer, FileTreeViews.Observer[T]] =
    new LinkedHashMap()

  override def onNext(t: T): Unit = {
    var cbs: List[FileTreeViews.Observer[T]] = null
    observers.synchronized {
      cbs = new ArrayList(observers.values)
    }
    val it: Iterator[FileTreeViews.Observer[T]] = cbs.iterator()
    while (it.hasNext) try it.next().onNext(t)
    catch {
      case e: Exception =>
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          Loggers.logException(logger, e)
        }

    }
  }

  override def onError(throwable: Throwable): Unit = {
    var cbs: List[FileTreeViews.Observer[T]] = null
    observers.synchronized {
      cbs = new ArrayList(observers.values)
    }
    val it: Iterator[FileTreeViews.Observer[T]] = cbs.iterator()
    while (it.hasNext) try it.next().onError(throwable)
    catch {
      case e: Exception =>
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          Loggers.logException(logger, e)
        }

    }
  }

  /**
   * Add an cacheObserver to receive events.
   *
   * @param observer the new cacheObserver
   * @return a handle to the added cacheObserver that can be used to halt observation using [[    com.swoval.files.Observers.removeObserver]] .
   */
  def addObserver(observer: Observer[_ >: T]): Int = {
    val key: Int = counter.getAndIncrement
    observers.synchronized {
      observers.put(key, observer.asInstanceOf[Observer[T]])
    }
    key
  }

  /**
   * Remove an instance of [[CacheObserver]] that was previously added using [[com.swoval.files.Observers.addObserver]].
   *
   * @param handle the handle to remove
   */
  def removeObserver(handle: Int): Unit = {
    observers.synchronized {
      observers.remove(handle)
    }
  }

  override def close(): Unit = {
    observers.clear()
  }

}
