package com.swoval
package files

import java.io.IOException
import java.nio.file.{ Files, Path }

import com.swoval.files.TestHelpers._
import com.swoval.files.impl.CachedDirectoryImpl
import com.swoval.files.test._
import com.swoval.functional.Filters.AllPass
import com.swoval.test._
import utest._

import scala.collection.JavaConverters._
import scala.concurrent.Future

object DataViewTest extends TestSuite {
  import RelativeFileTreeViewTest.RepositoryOps
  private def cached(path: Path, pathToInteger: TypedPath => Integer) = {
    new CachedDirectoryImpl[Integer](path, pathToInteger, Int.MaxValue, AllPass, true).init()
  }
  def directory: Future[Unit] = withTempFileSync { file =>
    val parent = file.getParent
    val converter = (p: TypedPath) => {
      if (p.isDirectory) throw new IOException("die")
      1: Integer
    }
    val dir = cached(parent, converter)
    val either = dir.getEntry.getValue
    either.leftValue.getMessage == "die"
    either.getOrElse(2) ==> 2
    dir.ls(recursive = true, AllPass) === Seq(file)
  }
  def subdirectory: Future[Unit] = withTempDirectorySync { dir =>
    val subdir = Files.createDirectory(dir.resolve("subdir"))
    val directory = cached(dir, (p: TypedPath) => {
      if (p.getPath.toString.contains("subdir")) throw new IOException("die")
      1: Integer
    })
    directory.getEntry.getValue.getOrElse(2) ==> 1
    directory
      .list(Integer.MAX_VALUE, AllPass)
      .asScala
      .map(e => e.getTypedPath.getPath -> e.getValue.getOrElse(3))
      .toSeq === Seq(subdir -> 3)
  }
  def file: Future[Unit] = withTempFileSync { file =>
    val parent = file.getParent
    val dir = cached(parent, (p: TypedPath) => {
      if (!p.isDirectory) throw new IOException("die")
      1: Integer
    })
    dir.getEntry.getValue.getOrElse(2) ==> 1
    dir
      .list(parent, Integer.MAX_VALUE, AllPass)
      .asScala
      .map(e => e.getTypedPath.getPath -> e.getValue.getOrElse(3))
      .toSeq === Seq(file -> 3)
  }
  val tests = Tests {
    'converter - {
      'exceptions - {
        'directory - directory
        'subdirectory - subdirectory
        'file - file
      }
    }
  }
}
