package com.swoval.files.impl

import java.nio.file.Path
import java.util.concurrent.TimeUnit

import com.swoval
import com.swoval.files.PathWatchers.Event
import com.swoval.files.TestHelpers._
import com.swoval.files.impl.apple.MacOSXWatchService
import com.swoval.files.test._
import com.swoval.files.{ BoundedWatchService, RegisterableWatchService, RegisterableWatchServices }
import com.swoval.logging.Logger
import com.swoval.runtime.Platform
import com.swoval.test.Implicits.executionContext
import com.swoval.test._
import utest._

import scala.collection.mutable

object NioPathWatcherOverflowTest extends TestSuite {
  def getBounded(size: Int, logger: Logger): RegisterableWatchService =
    if (Platform.isMac) new MacOSXWatchService(10, TimeUnit.MILLISECONDS, size, logger)
    else RegisterableWatchServices.get
  val tests: Tests = if (Platform.isJVM || !Platform.isMac) Tests {
    val subdirsToAdd = 200
    'overflows - withTempDirectory { dir =>
      implicit val logger: TestLogger = new CachingLogger
      val executor = Executor.make("NioPathWatcherOverflowTest-executor", logger)
      val subdirs = 1 to subdirsToAdd map { i =>
        dir.resolve(s"subdir-$i")
      }
      val subdirLatch = new CountDownLatch(subdirsToAdd)
      val fileLatch = new CountDownLatch(subdirsToAdd)
      val addedSubdirs = mutable.Set.empty[Path]
      val addedFiles = mutable.Set.empty[Path]
      val files = subdirs.map(_.resolve("file"))
      val callback = (e: Event) => {
        e.path.getFileName.toString match {
          case name if name.startsWith("subdir") && addedSubdirs.add(e.path) =>
            subdirLatch.countDown()
          case name if name == "file" && addedFiles.add(e.path) =>
            fileLatch.countDown()
          case _ =>
        }
      }
      usingAsync(
        PlatformWatcher.make(
          new BoundedWatchService(4, getBounded(2, logger)),
          new DirectoryRegistryImpl(),
          logger
        )) { c =>
        c.addObserver(callback)
        c.register(dir, Integer.MAX_VALUE)
        executor.run(() => subdirs.foreach(_.createDirectory()))
        subdirLatch
          .waitFor(DEFAULT_TIMEOUT) {
            subdirs.toSet === addedSubdirs.toSet
            executor.run(() => files.foreach(_.createFile()))
          }
          .flatMap { _ =>
            fileLatch.waitFor(DEFAULT_TIMEOUT) {
              files.toSet === addedFiles.toSet
            }
          }
      }
    }
  } else
    Tests('ignore - {
      if (swoval.test.verbose) println("Not running NioPathWatcher on scala.js on osx")
    })
}
