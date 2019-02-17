package com.swoval
package files

import java.io.IOException
import java.nio.file.Path

import com.swoval.files.FileTreeDataViews.{ CacheObserver, Entry }
import com.swoval.files.TestHelpers._
import com.swoval.files.impl.Provider
import com.swoval.files.test._
import com.swoval.files.test.platform.Bool
import com.swoval.functional.{ Filter, Filters, IOFunction }
import utest._

import scala.collection.JavaConverters._

trait FileCacheTest extends TestSuite { self: TestSuite =>
  def defaultProvider(implicit logger: TestLogger): FileTreeRepositoryProvider
  def getPath: IOFunction[TypedPath, Path] = (_: TypedPath).getPath

  def simpleCache(f: CacheEntry[Path] => Unit)(
      implicit provider: FileTreeRepositoryProvider): FileTreeRepository[Path] =
    FileCacheTest.get(getPath, getObserver(f))

  def lastModifiedCache(f: CacheEntry[LastModified] => Unit)(
      implicit provider: FileTreeRepositoryProvider): FileTreeRepository[LastModified] =
    FileCacheTest.get(LastModified(_: TypedPath), getObserver(f))

  def lastModifiedCache(onCreate: CacheEntry[LastModified] => Unit,
                        onUpdate: (CacheEntry[LastModified], CacheEntry[LastModified]) => Unit,
                        onDelete: CacheEntry[LastModified] => Unit)(
      implicit provider: FileTreeRepositoryProvider): FileTreeRepository[LastModified] =
    FileCacheTest.get(LastModified(_: TypedPath), getObserver(onCreate, onUpdate, onDelete))
}

object FileCacheTest {
  def get[T <: AnyRef](converter: IOFunction[TypedPath, T], cacheObserver: CacheObserver[T])(
      implicit provider: FileTreeRepositoryProvider): FileTreeRepository[T] = {
    val res = provider.followSymlinks(converter)
    res.addCacheObserver(cacheObserver)
    res
  }
  class LoopCacheObserver(val latch: CountDownLatch) extends FileTreeDataViews.CacheObserver[Path] {
    override def onCreate(newEntry: CacheEntry[Path]): Unit = {}
    override def onDelete(oldEntry: CacheEntry[Path]): Unit = {}
    override def onUpdate(oldEntry: CacheEntry[Path], newEntry: CacheEntry[Path]): Unit = {}
    override def onError(exception: IOException): Unit = latch.countDown()
  }

  implicit class FileCacheOps[T <: AnyRef](val fileCache: FileTreeRepository[T]) extends AnyVal {
    def ls(dir: Path): Seq[CacheEntry[T]] =
      fileCache.list(dir, Int.MaxValue, Filters.AllPass).asScala
    def ls(dir: Path, recursive: Boolean): Seq[CacheEntry[T]] =
      fileCache.list(dir, if (recursive) Int.MaxValue else 0, Filters.AllPass).asScala
    def ls[R >: CacheEntry[T]](dir: Path,
                               recursive: Boolean,
                               filter: Filter[R]): Seq[CacheEntry[T]] =
      fileCache.list(dir, if (recursive) Int.MaxValue else 0, filter).asScala

    def reg(dir: Path, recursive: Boolean = true): functional.Either[IOException, Bool] = {
      val res = fileCache.register(dir, recursive)
      assert(res.getOrElse[Bool](false))
      res
    }
  }
}

trait DefaultFileCacheTest extends FileCacheTest {
  override def defaultProvider(implicit testLogger: TestLogger): FileTreeRepositoryProvider =
    new Provider().getFileTreeRepositoryProvider
}

trait NioFileCacheTest extends FileCacheTest {
  override def defaultProvider(implicit logger: TestLogger): FileTreeRepositoryProvider =
    new Provider().getFileTreeRepositoryProvider
}
