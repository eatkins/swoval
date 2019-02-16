package com
package swoval
package files

import java.io.IOException
import java.lang
import java.nio.file.{ Files, Path }
import java.util.concurrent.TimeUnit

import com.swoval.files.FileTreeRepositories.NoFollowSymlinks
import com.swoval.files.PathWatchers.FollowSymlinks
import com.swoval.files.TestHelpers._
import com.swoval.files.api.{ Observer, PathWatcher }
import com.swoval.files.impl.{
  DirectoryRegistryImpl,
  PlatformWatcher,
  SwovalProviderImpl,
  SymlinkFollowingPathWatcherImpl
}
import com.swoval.files.test._
import com.swoval.logging.Logger
import com.swoval.runtime.Platform
import com.swoval.test._
import utest._

trait PathWatcherSymlinkTest extends TestSuite {
  def defaultWatcher(callback: PathWatchers.Event => _)(
      implicit testLogger: TestLogger): PathWatcher[PathWatchers.Event]
  class Wrapper[T](delegate: PathWatcher[T]) extends PathWatcher[T] {
    override def register(path: Path, maxDepth: Int): functional.Either[IOException, lang.Boolean] =
      delegate.register(path, maxDepth)
    override def unregister(path: Path): Unit = delegate.unregister(path)
    override def close(): Unit = delegate.close()
    override def addObserver(observer: Observer[_ >: T]): Int =
      delegate.addObserver(observer)
    override def removeObserver(handle: Int): Unit = delegate.removeObserver(handle)
  }
  class FollowWrapper[T](delegate: PathWatcher[T])
      extends Wrapper[T](delegate)
      with PathWatchers.FollowSymlinks[T]
  class NoFollowWrapper[T](delegate: PathWatcher[T])
      extends Wrapper[T](delegate)
      with PathWatchers.NoFollowSymlinks[T]
  val testsImpl = Tests {
    'follow - {
      'file - {
        'initial - {
          implicit val logger: TestLogger = new CachingLogger
          withTempDirectory { dir =>
            withTempFile { file =>
              val link = Files.createSymbolicLink(dir.resolve("link"), file)
              val latch = new CountDownLatch(1)
              usingAsync(defaultWatcher((e: PathWatchers.Event) => {
                if (e.getTypedPath.getPath == link) {
                  latch.countDown()
                }
              })) { c =>
                assert(c.register(dir, Integer.MAX_VALUE).isRight())
                Files.write(file, "foo".getBytes)
                latch.waitFor(DEFAULT_TIMEOUT) {
                  new String(Files.readAllBytes(file)) ==> "foo"
                }
              }
            }
          }
        }
        'added - {
          implicit val logger: TestLogger = new CachingLogger
          withTempDirectory { dir =>
            withTempFile { file =>
              val latch = new CountDownLatch(1)
              val link = dir.resolve("link")
              usingAsync(defaultWatcher((e: PathWatchers.Event) => {
                if (e.getTypedPath.getPath == link) {
                  latch.countDown()
                }
              })) { c =>
                assert(c.register(dir, Integer.MAX_VALUE).isRight())
                Files.createSymbolicLink(link, file)
                Files.write(file, "foo".getBytes)
                latch.waitFor(DEFAULT_TIMEOUT) {
                  new String(Files.readAllBytes(file)) ==> "foo"
                }
              }
            }
          }
        }
      }
      'directory - withTempDirectory { dir =>
        implicit val logger: TestLogger = new CachingLogger
        withTempDirectory { otherDir =>
          val file = otherDir.resolve("file").createFile()
          val latch = new CountDownLatch(1)
          val link = Files.createSymbolicLink(dir.resolve("link"), otherDir)
          val linkedFile = link.resolve("file")
          usingAsync(defaultWatcher((e: PathWatchers.Event) => {
            if (e.getTypedPath.getPath == linkedFile) {
              latch.countDown()
            }
          })) { c =>
            assert(c.register(dir, Integer.MAX_VALUE).isRight())
            Files.write(file, "foo".getBytes)
            latch.waitFor(DEFAULT_TIMEOUT) {
              new String(Files.readAllBytes(file)) ==> "foo"
            }
          }
        }
      }
    }
  }
}

object PathWatcherSymlinkTest extends PathWatcherSymlinkTest {
  override def defaultWatcher(callback: Predef.Function[PathWatchers.Event, _])(
      implicit testLogger: TestLogger): FollowSymlinks[PathWatchers.Event] = {
    val provider = SwovalProviderImpl.getDefaultProvider.getPathWatcherProvider
    val res = provider.followSymlinks()
    res.addObserver(callback)
    res
  }
  override val tests = testsImpl
}
//object NioPathWatcherSymlinkTest extends PathWatcherSymlinkTest {
//  override def defaultWatcher(callback: Predef.Function[PathWatchers.Event, _])(
//      implicit testLogger: TestLogger): PathWatcher[PathWatchers.Event] = {
//    val registry = new DirectoryRegistryImpl
//    val provider: PathWatcherProvider = new PathWatcherProvider {
//      private def newWatcher(logger: Logger) = PlatformWatcher.make(registry, logger)
//      override def noFollowSymlinks(
//          logger: Logger): PathWatchers.NoFollowSymlinks[PathWatchers.Event] =
//        new NoFollowWrapper[PathWatchers.Event](newWatcher(logger))
//      override def followSymlinks(logger: Logger): FollowSymlinks[PathWatchers.Event] = ???
//      override def polling(pollInterval: Long,
//                           timeUnit: TimeUnit,
//                           logger: Logger): PathWatcher[PathWatchers.Event] = ???
//    }
//    val res = new SymlinkFollowingPathWatcherImpl(provider.noFollowSymlinks(testLogger),
//                                                  registry,
//                                                  testLogger,
//                                                  provider)
//    res.addObserver(callback)
//    res
//  }
//  override val tests = if (Platform.isMac && Platform.isJVM) {
//    testsImpl
//  } else {
//    Tests {
//      'ignore - {
//        if (swoval.test.verbose)
//          println("Not running NioPathWatcherSymlinkTest on platform other than osx on the jvm")
//      }
//    }
//  }
//}
