package com.swoval.files.impl
import com.swoval.files.cache.{ CacheObserver, Entry }
class TestCacheUpdates[T] extends CacheUpdates[T]
object TestCacheUpdates {
  import scala.language.implicitConversions
  implicit def fromCacheUpdates[T](cacheUpdates: CacheUpdates[T]): TestCacheUpdates[T] =
    new TestCacheUpdates[T] {
      override def observe(cacheObserver: CacheObserver[T]): Unit =
        cacheUpdates.observe(cacheObserver)
      override def onCreate(newEntry: Entry[T]): Unit =
        cacheUpdates.onCreate(newEntry)
      override def onDelete(oldEntry: Entry[T]): Unit =
        cacheUpdates.onDelete(oldEntry)
      override def onUpdate(oldEntry: Entry[T], newEntry: Entry[T]): Unit =
        cacheUpdates.onUpdate(oldEntry, newEntry)
      override def onError(throwable: Throwable): Unit = cacheUpdates.onError(throwable)
      override def toString: String = cacheUpdates.toString
    }
}
