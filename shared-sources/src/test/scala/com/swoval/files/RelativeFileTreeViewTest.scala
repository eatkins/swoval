package com.swoval.files

import java.nio.file.{ Path, Paths }

import com.swoval.files.TestHelpers._
import com.swoval.files.api.FileTreeView
import com.swoval.files.impl.RelativeFileTreeViewImpl
import com.swoval.files.test._
import com.swoval.functional.Filter
import com.swoval.functional.Filters.AllPass
import com.swoval.runtime.Platform
import com.swoval.test._
import utest._

import scala.collection.JavaConverters._
import scala.concurrent.Future

object RelativeFileTreeViewTest {
  implicit class RepositoryOps[T <: AnyRef](val d: RelativeFileTreeView[T])(
      implicit f: T => TypedPath) {
    def ls(path: Path, recursive: Boolean, filter: Filter[_ >: TypedPath]): Seq[Path] =
      ls(path, if (recursive) Integer.MAX_VALUE else 0, filter)
    def ls(path: Path, depth: Int, filter: Filter[_ >: TypedPath]): Seq[Path] =
      d.list(path, depth, AllPass)
        .asScala
        .flatMap(t =>
          f(t) match {
            case tp if filter.accept(tp) => Some(tp.getPath)
            case _                       => None
        })
    def ls(recursive: Boolean, filter: Filter[_ >: TypedPath]): Seq[Path] =
      ls(if (recursive) Int.MaxValue else 0, filter)
    def ls(depth: Int, filter: Filter[_ >: TypedPath]): Seq[Path] =
      d.list(depth, new Filter[T] { override def accept(t: T): Boolean = filter.accept(f(t)) })
        .asScala
        .map(_.getPath)
  }
  def relativeTo(p: Path)(paths: Path*): Seq[Path] = paths.map(p.relativize)
}
import com.swoval.files.RelativeFileTreeViewTest._
class RelativeFileTreeViewTest(
    newFileTreeView: (Path, Int, Boolean) => RelativeFileTreeView[TypedPath])
    extends TestSuite {
  def newFileTreeView(path: Path): RelativeFileTreeView[TypedPath] =
    newFileTreeView(path, Integer.MAX_VALUE, false)
  def newFileTreeView(path: Path, maxDepth: Int): RelativeFileTreeView[TypedPath] =
    newFileTreeView(path, maxDepth, true)

  def pathFilter(f: TypedPath => Boolean): Filter[TypedPath] = (tp: TypedPath) => f(tp)

  object list {
    def empty: Future[Unit] = withTempDirectorySync { dir =>
      newFileTreeView(dir).ls(dir, recursive = true, AllPass) === Seq.empty[Path]
    }
    object files {
      def parent: Future[Unit] =
        withTempFileSync { file =>
          val parent = file.getParent
          newFileTreeView(parent).ls(parent, recursive = true, AllPass) === relativeTo(parent)(file)
        }
      def directly: Future[Unit] = withTempFileSync { file =>
        val parent = file.getParent
        newFileTreeView(parent).ls(file, -1, AllPass) === relativeTo(parent)(file)
      }
    }
    def resolution: Future[Unit] = withTempDirectory { dir =>
      withTempDirectory(dir) { subdir =>
        withTempFileSync(subdir) { f =>
          def parentEquals(dir: Path): Filter[TypedPath] =
            (tp: TypedPath) => tp.getPath.getParent == dir
          val dirFilter = parentEquals(dir)
          val subdirFilter = parentEquals(subdir)
          val directory = newFileTreeView(dir)
          directory.ls(recursive = true, dirFilter) === relativeTo(dir)(subdir)
          directory.ls(recursive = true, parentEquals(subdir)) === relativeTo(dir)(f)
          directory.ls(recursive = true, AllPass) === relativeTo(dir)(subdir, f)
        }
      }
    }
    object directories {
      def nonRecursive: Future[Unit] = withTempDirectory { dir =>
        withTempFile(dir) { f =>
          withTempDirectory(dir) { subdir =>
            withTempFileSync(subdir) { _ =>
              newFileTreeView(dir).ls(recursive = false, AllPass) === relativeTo(dir)(f, subdir)
            }
          }
        }
      }
      def recursive: Future[Unit] = withTempDirectory { dir =>
        withTempFile(dir) { f =>
          withTempDirectory(dir) { subdir =>
            withTempFileSync(subdir) { f2 =>
              newFileTreeView(dir)
                .ls(recursive = true, AllPass) === relativeTo(dir)(f, f2, subdir).toSet
            }
          }
        }
      }
    }
    def subdirectories: Future[Unit] = withTempDirectory { dir =>
      withTempDirectory(dir) { subdir =>
        withTempFileSync(subdir) { f =>
          newFileTreeView(dir).ls(subdir, recursive = true, AllPass) === relativeTo(dir)(f)
          newFileTreeView(dir).ls(Paths.get(s"$subdir.1"), recursive = true, AllPass) === Nil
        }
      }
    }
    def filter: Future[Unit] = withTempDirectory { dir =>
      withTempFile(dir) { f =>
        withTempDirectorySync(dir) { subdir =>
          newFileTreeView(dir)
            .ls(recursive = true, pathFilter(!(_: TypedPath).isDirectory)) === relativeTo(dir)(f)
          newFileTreeView(dir)
            .ls(recursive = true, pathFilter((_: TypedPath).isDirectory)) === relativeTo(dir)(
            subdir)
        }
      }
    }
  }
  def recursive: Future[Unit] = withTempDirectory { dir =>
    withTempDirectory(dir) { subdir =>
      withTempFileSync(subdir) { f =>
        assert(f.exists)
        newFileTreeView(subdir).ls(subdir, recursive = true, AllPass) === relativeTo(subdir)(f)
        newFileTreeView(dir, 0).ls(dir, recursive = true, AllPass) === relativeTo(dir)(subdir)
        newFileTreeView(dir).ls(dir, recursive = true, AllPass) === relativeTo(dir)(subdir, f)
        newFileTreeView(dir).ls(dir, recursive = false, AllPass) === relativeTo(dir)(subdir)
      }
    }
  }
  object depth {
    def nonnegative: Future[Unit] = withTempDirectory { dir =>
      withTempDirectory(dir) { subdir =>
        withTempFileSync(subdir) { file =>
          newFileTreeView(dir, 0).ls(dir, recursive = true, AllPass) === relativeTo(dir)(subdir)
          newFileTreeView(dir, 1).ls(dir, recursive = true, AllPass) === relativeTo(dir)(subdir,
                                                                                         file)
        }
      }
    }
    object negative {
      def ls(fileTreeView: FileTreeView[TypedPath], file: Path): Seq[Path] =
        fileTreeView.list(file, -1, AllPass).asScala.map(_.getPath)
      def file: Future[Unit] = withTempFileSync { file =>
        newFileTreeView(file, -1)
          .list(file, -1, AllPass)
          .asScala
          .toIndexedSeq
          .map(_.getPath) === relativeTo(file)(file)
      }
      def directory: Future[Unit] = withTempDirectorySync { dir =>
        newFileTreeView(dir, -1).ls(dir, -1, AllPass) === relativeTo(dir)(dir)
      }
      def parameter: Future[Unit] = withTempFileSync { file =>
        val dir = file.getParent
        val directory = newFileTreeView(dir, Integer.MAX_VALUE)
        directory.list(dir, -1, AllPass).asScala.map(_.getPath) === relativeTo(dir)(dir)
        directory.list(dir, 0, AllPass).asScala.map(_.getPath) === relativeTo(dir)(file)
      }
    }
  }
  object init {
    def accessDenied: Future[Unit] =
      if (!Platform.isWin) withTempDirectory { dir =>
        withTempDirectory(dir) { subdir =>
          withTempFileSync(subdir) { file =>
            subdir.toFile.setReadable(false)
            try {
              val directory = newFileTreeView(dir)
              directory.ls(dir, recursive = true, AllPass) === relativeTo(dir)(subdir)
            } finally {
              subdir.toFile.setReadable(true)
            }
          }
        }
      } else { Future.successful(()) }
  }
  object symlinks {
    def file: Future[Unit] = withTempFileSync { file =>
      val parent = file.getParent
      val link = parent.resolve("link") linkTo file
      newFileTreeView(parent).ls(parent, recursive = true, AllPass) === relativeTo(parent)(file,
                                                                                           link)
    }
    def directory: Future[Unit] = withTempDirectory { dir =>
      withTempDirectorySync { otherDir =>
        val link = dir.resolve("link") linkTo otherDir
        val file = otherDir.resolve("file").createFile()
        val dirFile = dir.resolve("link").resolve("file")
        newFileTreeView(dir, Integer.MAX_VALUE, true)
          .ls(dir, recursive = true, AllPass) === relativeTo(dir)(link, dirFile)
      }
    }
    object loop {
      def initial: Future[Unit] = withTempDirectory { dir =>
        withTempDirectorySync { otherDir =>
          val dirToOtherDirLink = dir.resolve("other") linkTo otherDir
          val otherDirToDirLink = otherDir.resolve("dir") linkTo dir
          newFileTreeView(dir, Integer.MAX_VALUE, true)
            .ls(dir, recursive = true, AllPass) === relativeTo(dir)(
            dirToOtherDirLink,
            dirToOtherDirLink.resolve("dir")).toSet
        }
      }
    }

  }
  val tests = Tests {
    'list - {
      'empty - list.empty
      'files - {
        'parent - list.files.parent
        'directly - list.files.directly
      }
      'resolution - list.resolution
      'directories {
        'nonRecursive - list.directories.nonRecursive
        'recursive - list.directories.recursive
      }
      //'subdirectories - list.subdirectories
      'filter - list.filter
    }
    'recursive - recursive
    'depth - {
      'nonnegative - depth.nonnegative
      'negative - {
        'file - depth.negative.file
        'directory - depth.negative.directory
        'parameter - depth.negative.parameter
      }
    }
//    'init - {
//      'accessDenied - init.accessDenied
//    }
    'symlinks - {
      'file - symlinks.file
      'directory - symlinks.directory
      'loop - symlinks.loop.initial
    }
  }
}
//object DirectoryFileTreeViewTest extends RelativeFileTreeViewTest(FileTreeViews.cached)
object DefaultRelativeFileTreeViewTest
    extends RelativeFileTreeViewTest((path, depth, follow: Boolean) => {
      new RelativeFileTreeViewImpl(path,
                                   depth,
                                   AllPass,
                                   if (follow) FileTreeViews.followSymlinks
                                   else FileTreeViews.noFollowSymlinks)
    })
