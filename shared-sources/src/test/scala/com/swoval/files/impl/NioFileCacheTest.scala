package com.swoval.files
package impl

import com.swoval.files.test.TestLogger

trait NioFileCacheTest extends FileCacheTest {
  override def defaultProvider(implicit logger: TestLogger): FileTreeRepositoryProvider = {
    val pathWatcherProvider = new PathWatcherProviderImpl(logger) {
      override def noFollowSymlinks(): PathWatchers.NoFollowSymlinks[PathWatchers.Event] =
        this.noFollowSymlinks(new DirectoryRegistryImpl, logger, true)
    }
    new FileTreeRepositoryProviderImpl(pathWatcherProvider, logger)
  }
}
