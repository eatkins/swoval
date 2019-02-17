package com.swoval.files.impl
import java.io.IOException

import com.swoval.files.{ CacheEntry, FileTreeDataViews }
class TestCacheUpdates[T] extends CacheUpdates[T]
object TestCacheUpdates {
  import scala.language.implicitConversions
  implicit def fromCacheUpdates[T](cacheUpdates: CacheUpdates[T]): TestCacheUpdates[T] =
    new TestCacheUpdates[T] {
      override def observe(cacheObserver: FileTreeDataViews.CacheObserver[T]): Unit =
        cacheUpdates.observe(cacheObserver)
      override def onCreate(newEntry: CacheEntry[T]): Unit =
        cacheUpdates.onCreate(newEntry)
      override def onDelete(oldEntry: CacheEntry[T]): Unit =
        cacheUpdates.onDelete(oldEntry)
      override def onUpdate(oldEntry: CacheEntry[T], newEntry: CacheEntry[T]): Unit =
        cacheUpdates.onUpdate(oldEntry, newEntry)
      override def onError(exception: IOException): Unit = cacheUpdates.onError(exception)
      override def toString: String = cacheUpdates.toString
    }
}
