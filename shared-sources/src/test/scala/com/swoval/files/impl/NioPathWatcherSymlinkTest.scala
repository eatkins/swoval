package com.swoval.files
package impl

import com.swoval.files.TestHelpers._
import com.swoval.files.api.PathWatcher
import com.swoval.files.test.TestLogger
import com.swoval.runtime.Platform
import utest._

object NioPathWatcherSymlinkTest extends PathWatcherSymlinkTest {
  override def defaultWatcher(callback: PathWatchers.Event => _)(
      implicit testLogger: TestLogger): PathWatcher[PathWatchers.Event] = {
    val registry = new DirectoryRegistryImpl
    val provider: PathWatcherProvider = new PathWatcherProviderImpl(testLogger) {
      override def noFollowSymlinks(): PathWatchers.NoFollowSymlinks[PathWatchers.Event] =
        new NoFollowWrapper(
          new NioPathWatcher(registry, RegisterableWatchServices.get(), testLogger))
    }
    val res = new SymlinkFollowingPathWatcherImpl(provider.noFollowSymlinks(),
                                                  registry,
                                                  testLogger,
                                                  provider)
    res.addObserver(callback)
    res
  }
  override val tests = if (Platform.isMac && Platform.isJVM) {
    testsImpl
  } else {
    Tests {
      'ignore - {
        if (com.swoval.test.verbose)
          println("Not running NioPathWatcherSymlinkTest on platform other than osx on the jvm")
      }
    }
  }
}
