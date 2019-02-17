package com.swoval
package files

import java.io.IOException
import java.nio.file.Path

import com.swoval.files.FileTreeDataViews.{ CacheObserver, Entry }
import com.swoval.files.TestHelpers._
import com.swoval.files.impl.Provider
import com.swoval.files.test._
import com.swoval.files.test.platform.Bool
import com.swoval.functional.{ Converter, Filter, Filters }
import utest._

import scala.collection.JavaConverters._

trait FileCacheTest extends TestSuite { self: TestSuite =>
  def defaultProvider(implicit logger: TestLogger): FileTreeRepositoryProvider
  def identity: Converter[Path] = (_: TypedPath).getPath

  def simpleCache(f: Entry[Path] => Unit)(
      implicit provider: FileTreeRepositoryProvider): FileTreeRepository[Path] =
    FileCacheTest.get(identity, getObserver(f))

  def lastModifiedCache(f: Entry[LastModified] => Unit)(
      implicit provider: FileTreeRepositoryProvider): FileTreeRepository[LastModified] =
    FileCacheTest.get(LastModified(_: TypedPath), getObserver(f))

  def lastModifiedCache(onCreate: Entry[LastModified] => Unit,
                        onUpdate: (Entry[LastModified], Entry[LastModified]) => Unit,
                        onDelete: Entry[LastModified] => Unit)(
      implicit provider: FileTreeRepositoryProvider): FileTreeRepository[LastModified] =
    FileCacheTest.get(LastModified(_: TypedPath), getObserver(onCreate, onUpdate, onDelete))
}

object FileCacheTest {
  def get[T <: AnyRef](converter: Converter[T], cacheObserver: CacheObserver[T])(
      implicit provider: FileTreeRepositoryProvider): FileTreeRepository[T] = {
    val res = provider.followSymlinks(converter)
    res.addCacheObserver(cacheObserver)
    res
  }
  class LoopCacheObserver(val latch: CountDownLatch) extends FileTreeDataViews.CacheObserver[Path] {
    override def onCreate(newEntry: Entry[Path]): Unit = {}
    override def onDelete(oldEntry: Entry[Path]): Unit = {}
    override def onUpdate(oldEntry: Entry[Path], newEntry: Entry[Path]): Unit = {}
    override def onError(exception: IOException): Unit = latch.countDown()
  }

  implicit class FileCacheOps[T <: AnyRef](val fileCache: FileTreeRepository[T]) extends AnyVal {
    def ls(dir: Path): Seq[Entry[T]] =
      fileCache.list(dir, Int.MaxValue, Filters.AllPass).asScala
    def ls(dir: Path, recursive: Boolean): Seq[Entry[T]] =
      fileCache.list(dir, if (recursive) Int.MaxValue else 0, Filters.AllPass).asScala
    def ls[R >: Entry[T]](dir: Path, recursive: Boolean, filter: Filter[R]): Seq[Entry[T]] =
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
