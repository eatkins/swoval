package com
package swoval
package files

import java.io.IOException
import java.lang
import java.nio.file.{ Files, Path }

import com.swoval.files.PathWatchers.FollowSymlinks
import com.swoval.files.TestHelpers._
import com.swoval.files.api.{ Observer, PathWatcher }
import com.swoval.files.impl.SwovalProviderImpl
import com.swoval.files.test._
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
