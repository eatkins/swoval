package com.swoval.files
package impl

import java.util.concurrent.TimeUnit

import com.swoval.files
import com.swoval.files.api.{ FileTreeView, PathWatcher }
import com.swoval.files.test.TestLogger

class Provider(implicit testLogger: TestLogger) extends SwovalProvider {
  val pathWatcherProvider = new PathWatcherProviderImpl(testLogger)
  override def getFileTreeRepositoryProvider: FileTreeRepositoryProvider =
    new FileTreeRepositoryProviderImpl(pathWatcherProvider, testLogger)
  override def getFileTreeViewProvider: files.FileTreeViewProvider =
    new files.FileTreeViewProvider {
      override def get(followSymlinks: Boolean): FileTreeView[TypedPath] =
        new FileTreeViewProviderImpl().get(followSymlinks)
    }
  override def getPathWatcherProvider: PathWatcherProvider = new PathWatcherProvider {
    override def noFollowSymlinks(): PathWatchers.NoFollowSymlinks[PathWatchers.Event] =
      pathWatcherProvider.noFollowSymlinks()

    override def followSymlinks(): PathWatchers.FollowSymlinks[PathWatchers.Event] =
      pathWatcherProvider.followSymlinks(testLogger)
    override def polling(pollInterval: Long, timeUnit: TimeUnit): PathWatcher[PathWatchers.Event] =
      new PathWatcherProviderImpl.NoFollowWrapper(
        new PollingPathWatcher(pollInterval, timeUnit, true, testLogger))
  }
}
