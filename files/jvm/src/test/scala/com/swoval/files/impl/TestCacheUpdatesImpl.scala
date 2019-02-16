package com.swoval.files.impl
import java.io.IOException

import com.swoval.files.FileTreeDataViews

class TestCacheUpdates[T] extends CacheUpdates[T]
object TestCacheUpdates {
  import scala.language.implicitConversions
  implicit def fromCacheUpdates[T](cacheUpdates: CacheUpdates[T]): TestCacheUpdates[T] =
    new TestCacheUpdates[T] {
      override def observe(cacheObserver: FileTreeDataViews.CacheObserver[T]): Unit =
        cacheUpdates.observe(cacheObserver)
      override def onCreate(newEntry: FileTreeDataViews.Entry[T]): Unit =
        cacheUpdates.onCreate(newEntry)
      override def onDelete(oldEntry: FileTreeDataViews.Entry[T]): Unit =
        cacheUpdates.onDelete(oldEntry)
      override def onUpdate(oldEntry: FileTreeDataViews.Entry[T],
                            newEntry: FileTreeDataViews.Entry[T]): Unit =
        cacheUpdates.onUpdate(oldEntry, newEntry)
      override def onError(exception: IOException): Unit = cacheUpdates.onError(exception)
      override def toString: String = cacheUpdates.toString
    }
}
