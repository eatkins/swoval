package com.swoval

package files

import java.io.{ File, FileFilter, IOException }
import java.nio.file.{ Path, Paths }

import com.swoval.files.FileTreeDataViews.{ CacheObserver, Entry }
import com.swoval.files.api.{ Observer, PathWatcher }
import com.swoval.files.impl.functional.{ Consumer, EitherImpl }
import com.swoval.files.test.platform.Bool
import com.swoval.functional.{ Filter, IOFunction }
import com.swoval.runtime.Platform
import com.swoval.test._
import utest._

/**
 * Provides helper functions to make it more convenient to test the classes in com.swoval.files. It
 * provides numerous implicit classes to convert scala functions to java functional interfaces.
 * These conversions are to support scala 2.10 and scala 2.11 without the experimental compiler
 * flag set.
 *
 */
object TestHelpers extends PlatformFiles {

  val baseDir: Path = Paths.get("").toAbsolutePath
  val targetDir: Path = baseDir.getFileName.toString match {
    case "js" | "jvm" => baseDir.resolve("target")
    case _ =>
      baseDir.resolve("files").resolve(if (Platform.isJVM) "jvm" else "js").resolve("target")
  }

  val Ignore: CacheObserver[_] = getObserver[Path]((_: CacheEntry[Path]) => {})

  def getObserver[T <: AnyRef](
      oncreate: CacheEntry[T] => Unit,
      onupdate: (CacheEntry[T], CacheEntry[T]) => Unit,
      ondelete: CacheEntry[T] => Unit,
      onerror: IOException => Unit = _ => {}): FileTreeDataViews.CacheObserver[T] =
    new FileTreeDataViews.CacheObserver[T] {
      override def onCreate(newEntry: CacheEntry[T]): Unit = oncreate(newEntry)

      override def onDelete(oldEntry: CacheEntry[T]): Unit = ondelete(oldEntry)

      override def onUpdate(oldEntry: CacheEntry[T], newEntry: CacheEntry[T]): Unit =
        onupdate(oldEntry, newEntry)

      override def onError(exception: IOException): Unit = onerror(exception)
    }

  def getObserver[T <: AnyRef](
      onUpdate: CacheEntry[T] => Unit): FileTreeDataViews.CacheObserver[T] =
    getObserver[T](onUpdate, (_: CacheEntry[T], e: CacheEntry[T]) => onUpdate(e), onUpdate)

  implicit class PathWatcherOps[T](val watcher: PathWatcher[T]) extends AnyVal {
    def register(path: Path, recursive: Boolean): functional.Either[IOException, Bool] =
      watcher.register(path, if (recursive) Integer.MAX_VALUE else 0)

    def register(path: Path): functional.Either[IOException, Bool] =
      register(path, recursive = true)
  }

  implicit class EitherOps[L, R](val either: functional.Either[L, R]) extends AnyVal {
    def getOrElse[U >: R](u: U): U = EitherImpl.getOrElse(either, u)
    def get(): R = EitherImpl.getRight(either)
  }

  implicit class IOFunctionFunctionOps[T, R](val f: T => R) extends IOFunction[T, R] {
    override def apply(t: T): R = f(t)
  }

  implicit class FileFilterFunctionOps(val f: File => Boolean) extends FileFilter {
    override def accept(pathname: File): Boolean = f(pathname)
  }

  implicit class FilterOps[T](val f: T => Boolean) extends functional.Filter[T] {
    override def accept(t: T): Boolean = f(t)
  }

  implicit class EntryOps[T](val entry: CacheEntry[T]) {
    def value: T = entry.getValue.get
    def path: Path = entry.getTypedPath.getPath
  }
  implicit class EventOps(val event: PathWatchers.Event) {
    def path: Path = event.getTypedPath.getPath
  }

  implicit class RunableOps(val f: () => _) extends Runnable {
    override def run(): Unit = f()
  }

  implicit class EntryFilterFunctionOps[T](val f: CacheEntry[T] => Boolean)
      extends Filter[CacheEntry[T]] {
    override def accept(cacheEntry: CacheEntry[T]): Boolean = f(cacheEntry)
  }

  implicit class EntryAsTypedPath(val e: CacheEntry[_]) extends TypedPath {
    override def getPath: Path = tp.getPath
    override def exists(): Boolean = tp.exists()
    override def isDirectory: Boolean = tp.isDirectory
    override def isFile: Boolean = tp.isFile
    override def isSymbolicLink: Boolean = tp.isSymbolicLink
    val tp: TypedPath = e.getTypedPath
  }

  implicit class CallbackOps(f: PathWatchers.Event => _) extends Observer[PathWatchers.Event] {
    override def onError(t: Throwable): Unit = {}
    override def onNext(t: PathWatchers.Event): Unit = f(t)
  }
  implicit class CacheObserverFunctionOps[T](val f: CacheEntry[T] => Unit)
      extends FileTreeDataViews.CacheObserver[T] {
    override def onCreate(newCachedPath: CacheEntry[T]): Unit = f(newCachedPath)

    override def onDelete(oldCachedPath: CacheEntry[T]): Unit = f(oldCachedPath)

    override def onUpdate(oldCachedPath: CacheEntry[T], newCachedPath: CacheEntry[T]): Unit =
      f(newCachedPath)

    override def onError(exception: IOException): Unit = {}
  }

  implicit class ConsumerFunctionOps[T](val f: T => Unit) extends Consumer[T] {
    override def accept(t: T): Unit = f(t)
  }

  implicit class SeqPathOps[T](val l: Seq[Path]) extends AnyVal {
    def ===(r: Seq[Path]): Unit = new RichTraversable(l) === r

    def ===(r: Set[Path]): Unit = new RichTraversable(l.toSet) === r
  }

  object EntryOps {

    implicit class SeqEntryOps[T](val l: Seq[CacheEntry[T]]) extends AnyVal {
      def ===(r: Seq[Path]): Unit = new RichTraversable(l.map(_.getTypedPath.getPath)) === r

      def ===(r: Set[Path]): Unit = new RichTraversable(l.map(_.getTypedPath.getPath).toSet) === r
    }

  }

  implicit class TestPathOps(val path: java.nio.file.Path) extends AnyVal {
    def ===(other: java.nio.file.Path): Unit = {
      if (path.normalize != other.normalize) {
        path.normalize ==> other.normalize
      }
    }
  }

}
