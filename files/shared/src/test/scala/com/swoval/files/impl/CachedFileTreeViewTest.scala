package com.swoval.files.impl
import java.io.IOException
import java.nio.file.Path

import com.swoval.files.test.{
  FileBytes,
  LastModified,
  withTempDirectory,
  withTempDirectorySync,
  withTempFileSync
}
import utest.{ TestSuite, Tests, assert }

import scala.collection.mutable
import scala.concurrent.Future

object CachedFileTreeViewTest extends TestSuite {
  def newCachedView(path: Path): CachedDirectory[Path] = newCachedView(path, Integer.MAX_VALUE)
  def newCachedView(path: Path, maxDepth: Int): CachedDirectory[Path] =
    newCachedView(path, maxDepth, followLinks = true)
  def newCachedView(path: Path, maxDepth: Int, followLinks: Boolean): CachedDirectory[Path] =
    new CachedDirectoryImpl(TestTypedPaths.get(path),
                            (_: TypedPath).getPath,
                            maxDepth,
                            AllPass,
                            followLinks)
      .init()
  class Updates[T <: AnyRef](u: CacheUpdates[T]) {
    private[this] var _creations: Seq[Entry[T]] = Nil
    private[this] var _deletions: Seq[Entry[T]] = Nil
    private[this] var _updates: Seq[(Entry[T], Entry[T])] = Nil
    u.observe(new CacheObserver[T] {
      override def onCreate(newEntry: Entry[T]): Unit = _creations :+= newEntry
      override def onDelete(oldEntry: Entry[T]): Unit = _deletions :+= oldEntry
      override def onUpdate(oldEntry: Entry[T], newEntry: Entry[T]): Unit =
        _updates :+= (oldEntry, newEntry)
      override def onError(exception: IOException): Unit = {}
    })
    def creations: Seq[Entry[T]] = _creations
    def deletions: Seq[Entry[T]] = _deletions
    def updates: Seq[(Entry[T], Entry[T])] = _updates
  }
  implicit class UpdateOps[T <: AnyRef](val u: CacheUpdates[T]) extends AnyVal {
    def toUpdates: CachedFileTreeViewTest.Updates[T] = new CachedFileTreeViewTest.Updates(u)
  }

  object add {
    def file: Future[Unit] = withTempDirectory { dir =>
      val directory = newCachedView(dir)
      withTempFileSync(dir) { f =>
        directory.ls(f, recursive = false, AllPass) === Seq.empty
        assert(
          directory.update(TestTypedPaths.get(f, TestEntries.FILE)).toUpdates.creations.nonEmpty)
        directory.ls(f, -1, AllPass) === Seq(f)
      }
    }
    def directory: Future[Unit] = withTempDirectory { dir =>
      val directory = newCachedView(dir)
      withTempDirectory(dir) { subdir =>
        withTempFileSync(subdir) { f =>
          directory.ls(f, recursive = true, AllPass) === Seq.empty
          assert(
            directory
              .update(TestTypedPaths.get(f, TestEntries.FILE))
              .toUpdates
              .creations
              .nonEmpty)
          directory.ls(dir, recursive = true, AllPass) === Seq(subdir, f)
        }
      }
    }
    def sequentially: Future[Unit] = withTempDirectory { dir =>
      val directory = newCachedView(dir)
      withTempDirectory(dir) { subdir =>
        assert(
          directory
            .update(TestTypedPaths.get(subdir, TestEntries.DIRECTORY))
            .toUpdates
            .creations
            .nonEmpty)
        withTempFileSync(subdir) { f =>
          assert(
            directory
              .update(TestTypedPaths.get(f, TestEntries.FILE))
              .toUpdates
              .creations
              .nonEmpty)
          directory.ls(recursive = true, AllPass) === Set(subdir, f)
        }
      }
    }
    def recursive: Future[Unit] = withTempDirectory { dir =>
      val directory = newCachedView(dir)
      withTempDirectory(dir) { subdir =>
        withTempFileSync(subdir) { f =>
          assert(
            directory
              .update(TestTypedPaths.get(f, TestEntries.FILE))
              .toUpdates
              .creations
              .nonEmpty)
          directory.ls(recursive = true, AllPass) === Set(f, subdir)
        }
      }
    }
    object overlapping {
      def base: Future[Unit] = withTempDirectory { dir =>
        val directory = newCachedView(dir, 0)
        withTempDirectory(dir) { subdir =>
          withTempFileSync(subdir) { file =>
            directory.update(TestTypedPaths.get(subdir, TestEntries.DIRECTORY))
            directory.ls(recursive = true, AllPass) === Set(subdir)
          }
        }
      }
      def nested: Future[Unit] = withTempDirectory { dir =>
        withTempDirectory(dir) { subdir =>
          val directory = newCachedView(dir, 2)
          withTempDirectory(subdir) { nestedSubdir =>
            withTempDirectory(nestedSubdir) { deepNestedSubdir =>
              withTempFileSync(deepNestedSubdir) { file =>
                directory.update(TestTypedPaths.get(nestedSubdir, TestEntries.DIRECTORY))
                directory.ls(recursive = true, AllPass) === Set(subdir,
                                                                nestedSubdir,
                                                                deepNestedSubdir)
              }
            }
          }
        }
      }
    }
  }
  object update {
    object directory {
      def simple: Future[Unit] = withTempDirectory { dir =>
        withTempDirectorySync(dir) { subdir =>
          val directory = newCachedView(dir)
          val file = subdir.resolve("file").createFile()
          val updates =
            directory.update(TestTypedPaths.get(subdir, TestEntries.DIRECTORY)).toUpdates
          val typedPath = TestTypedPaths.get(subdir, TestEntries.DIRECTORY)
          val entry: Entry[Path] = TestEntries.get(typedPath, (_: TypedPath).getPath, typedPath)
          updates.updates === Seq(entry -> entry)
          updates.creations === Seq(file)
        }
      }
      def remove: Future[Unit] = withTempDirectory { dir =>
        withTempFileSync(dir) { file =>
          val directory = newCachedView(dir)
          file.delete()
          val updates = directory.update(TestTypedPaths.get(file)).toUpdates
          updates.deletions === Seq(file)
        }
      }
      def concurrentRemove: Future[Unit] = withTempDirectory { dir =>
        val directory = newCachedView(dir)
        withTempDirectorySync(dir) { subdir =>
          val typedPath = TestTypedPaths.get(subdir)
          subdir.delete()
          val updates = directory.update(typedPath).toUpdates
          assert(updates.creations.isEmpty)
          assert(updates.deletions.isEmpty)
          assert(updates.updates.isEmpty)
        }
      }
      object nested {
        def created: Future[Unit] = withTempDirectory { dir =>
          withTempDirectorySync(dir) { subdir =>
            val directory = newCachedView(dir)
            val nestedSubdir = subdir.resolve("nested").createDirectory()
            val nestedFile = nestedSubdir.resolve("file").createFile()
            val updates =
              directory.update(TestTypedPaths.get(subdir, TestEntries.DIRECTORY)).toUpdates
            val typedPath = TestTypedPaths.get(subdir, TestEntries.DIRECTORY)
            val entry: Entry[Path] =
              TestEntries.get(typedPath, (_: TypedPath).getPath, typedPath)
            updates.updates === Seq(entry -> entry)
            updates.creations === Set(nestedSubdir, nestedFile)
          }
        }
        def removed: Future[Unit] = withTempDirectory { dir =>
          withTempDirectorySync(dir) { subdir =>
            val nestedSubdir = subdir.resolve("nested").createDirectory()
            val nestedFile = nestedSubdir.resolve("file").createFile()
            val directory = newCachedView(dir)
            nestedSubdir.deleteRecursive()
            val updates =
              directory.update(TestTypedPaths.get(subdir, TestEntries.DIRECTORY)).toUpdates
            val typedPath = TestTypedPaths.get(subdir, TestEntries.DIRECTORY)
            val entry: Entry[Path] =
              TestEntries.get(TestTypedPaths.get(subdir, TestEntries.DIRECTORY),
                              (_: TypedPath).getPath,
                              typedPath)
            updates.updates === Seq(entry -> entry)
            updates.deletions === Set(nestedSubdir, nestedFile)
          }
        }
      }
      def subfiles: Future[Unit] = withTempDirectorySync { dir =>
        val directory = newCachedView(dir)
        directory.list(Integer.MAX_VALUE, AllPass).asScala.toSeq === Seq.empty[TypedPath]
        val subdir: Path = dir.resolve("subdir").resolve("nested").createDirectories()
        val files = 1 to 2 map (i => subdir.resolve(s"file-$i").createFile())
        val found = mutable.Set.empty[Path]
        val updates = directory.update(TestTypedPaths.get(files.last, TestEntries.FILE))
        updates.observe(CacheObservers.fromObserver(new Observer[Entry[Path]] {
          override def onError(t: Throwable): Unit = {}
          override def onNext(t: Entry[Path]): Unit = found.add(t.getTypedPath.getPath())
        }))
        val expected = (files :+ subdir :+ subdir.getParent).toSet
        found.toSet === expected
        directory.update(TestTypedPaths.get(subdir.getParent, TestEntries.DIRECTORY))
        directory.list(Integer.MAX_VALUE, AllPass).asScala.toSeq.map(_.getPath) === expected
      }
    }
    def depth: Future[Unit] = withTempDirectory { dir =>
      val directory = newCachedView(dir, 0)
      withTempDirectory(dir) { subdir =>
        withTempDirectorySync(subdir) { nestedSubdir =>
          directory.ls(recursive = true, AllPass) === Seq.empty[Path]
          directory.update(TestTypedPaths.get(subdir, TestEntries.DIRECTORY))
          directory.ls(recursive = true, AllPass) === Seq(subdir)
          directory.update(TestTypedPaths.get(nestedSubdir, TestEntries.DIRECTORY))
          directory.ls(recursive = true, AllPass) === Seq(subdir)
        }
      }
    }
  }
  object remove {
    def direct: Future[Unit] = withTempDirectory { dir =>
      withTempFileSync(dir) { f =>
        val directory = newCachedView(dir)
        directory.ls(recursive = false, AllPass) === Seq(f)
        assert(Option(directory.remove(f)).isDefined)
        directory.ls(recursive = false, AllPass) === Seq.empty[Path]
      }
    }
    def recursive: Future[Unit] = withTempDirectory { dir =>
      withTempDirectory(dir) { subdir =>
        withTempDirectory(subdir) { nestedSubdir =>
          withTempFileSync(nestedSubdir) { f =>
            val directory = newCachedView(dir)
            def ls = directory.ls(recursive = true, AllPass)
            ls === Set(f, subdir, nestedSubdir)
            directory.remove(f).asScala === Seq(f)
            ls === Set(subdir, nestedSubdir)
          }
        }
      }
    }
  }
  object subTypes {
    private def newDirectory[T <: AnyRef](path: Path, converter: TypedPath => T) =
      new CachedDirectoryImpl(TestTypedPaths.get(path),
                              converter,
                              Integer.MAX_VALUE,
                              (_: TypedPath) => true,
                              true).init()
    def overrides: Future[Unit] = withTempFileSync { f =>
      val dir = newDirectory(f.getParent, LastModified(_: TypedPath))
      val lastModified = f.lastModified
      val updatedLastModified = 2000
      f.setLastModifiedTime(updatedLastModified)
      f.lastModified ==> updatedLastModified
      val cachedFile = dir.listEntries(f, Integer.MAX_VALUE, AllPass).get(0)
      cachedFile.getValue().get().lastModified ==> lastModified
    }
    def newFields: Future[Unit] = withTempFileSync { f =>
      f.write("foo")
      val initialBytes = "foo".getBytes.toIndexedSeq
      val dir = newDirectory(f.getParent, FileBytes(_: TypedPath))
      def filter(bytes: Seq[Byte]): Filter[Entry[FileBytes]] =
        (e: Entry[FileBytes]) => e.getValue.get().bytes == bytes
      val cachedFile = dir.listEntries(f, Integer.MAX_VALUE, filter(initialBytes)).get(0)
      cachedFile.getValue.get().bytes ==> initialBytes
      f.write("bar")
      val newBytes = "bar".getBytes
      cachedFile.getValue().get().bytes ==> initialBytes
      f.getBytes ==> newBytes
      dir.update(TestTypedPaths.get(f, TestEntries.FILE))
      val newCachedFile = dir.listEntries(f, Integer.MAX_VALUE, filter(newBytes)).get(0)
      newCachedFile.getValue().get().bytes.toSeq ==> newBytes.toSeq
      dir.listEntries(f, Integer.MAX_VALUE, filter(initialBytes)).asScala.toSeq === Seq
        .empty[Path]
    }
  }
  object symlinks {
    object indirect {
      def remoteLink: Future[Unit] = withTempDirectory { dir =>
        withTempDirectorySync { otherDir =>
          val dirToOtherDirLink = dir.resolve("other") linkTo otherDir
          val otherDirToDirLink = otherDir.resolve("dir") linkTo dir
          val directory = newCachedView(dir, Integer.MAX_VALUE, followLinks = true)
          directory.ls(dir, recursive = true, AllPass) === Set(dirToOtherDirLink,
                                                               dirToOtherDirLink.resolve("dir"))
          otherDirToDirLink.delete()
          otherDirToDirLink.createDirectory()
          val nestedFile = otherDirToDirLink.resolve("file").createFile()
          val file = dirToOtherDirLink.resolve("dir").resolve("file")
          directory.update(TestTypedPaths.get(dir, TestEntries.DIRECTORY))
          directory.ls(dir, recursive = true, AllPass) === Set(dirToOtherDirLink,
                                                               file.getParent,
                                                               file)
        }
      }
      def localLink: Future[Unit] = withTempDirectory { dir =>
        withTempDirectorySync { otherDir =>
          val dirToOtherDirLink = dir.resolve("other") linkTo otherDir
          val otherDirToDirLink = otherDir.resolve("dir") linkTo dir
          val directory = newCachedView(dir, Integer.MAX_VALUE, followLinks = true)
          directory.ls(dir, recursive = true, AllPass) === Set(dirToOtherDirLink,
                                                               dirToOtherDirLink.resolve("dir"))
          dirToOtherDirLink.delete()
          dirToOtherDirLink.createDirectory()
          val nestedFile = dirToOtherDirLink.resolve("file").createFile()
          directory.update(TestTypedPaths.get(dir, TestEntries.DIRECTORY))
          directory.ls(dir, recursive = true, AllPass) === Set(dirToOtherDirLink, nestedFile)
        }
      }
    }
    // This test is different from those above because it calls update with dirToOtherDirLink
    // rather than with dir
    def direct: Future[Unit] = withTempDirectory { dir =>
      withTempDirectorySync { otherDir =>
        val dirToOtherDirLink = dir.resolve("other") linkTo otherDir
        val otherDirToDirLink = otherDir.resolve("dir") linkTo dir
        val directory = newCachedView(dir, Integer.MAX_VALUE, followLinks = true)
        directory.ls(dir, recursive = true, AllPass) === Set(dirToOtherDirLink,
                                                             dirToOtherDirLink.resolve("dir"))
        dirToOtherDirLink.delete()
        dirToOtherDirLink.createDirectory()
        val nestedFile = dirToOtherDirLink.resolve("file").createFile()
        directory.update(TestTypedPaths.get(dirToOtherDirLink, TestEntries.DIRECTORY))
        directory.ls(dir, recursive = true, AllPass) === Set(dirToOtherDirLink, nestedFile)
      }
    }
  }

  val tests = Tests {
    'add - {
      'file - add.file
      'directory - add.directory
      'sequentially - add.sequentially
      'recursive - add.recursive
      'overlapping - {
        'base - add.overlapping.base
        'nested - add.overlapping.nested
      }
    }
    'update - {
      'directory - {
        'simple - update.directory.simple
        'remove - update.directory.remove
        'concurrentRemove - update.directory.concurrentRemove
        'nested - {
          'created - update.directory.nested.created
          'removed - update.directory.nested.removed
        }
        'subfiles - update.directory.subfiles
      }
      'depth - update.depth
    }
    'remove - {
      'direct - remove.direct
      'recursive - remove.recursive
    }

    'subTypes - {
      'overrides - subTypes.overrides
      'newFields - subTypes.newFields
    }
    'symlinks - {
      'indirect - {
        'remoteLink - symlinks.indirect.remoteLink
        'localLink - symlinks.indirect.localLink
      }
      'direct - symlinks.direct
    }
  }
}
