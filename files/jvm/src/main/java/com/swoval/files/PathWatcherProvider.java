package com.swoval.files;

import com.swoval.files.PathWatchers.Event;
import com.swoval.files.PathWatchers.FollowSymlinks;
import com.swoval.files.PathWatchers.NoFollowSymlinks;
import com.swoval.files.api.PathWatcher;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public interface PathWatcherProvider {
  /**
   * Create a PathWatcher that will not follow symlinks. The implementation will be platform
   * dependent.
   *
   * @return a PathWatcher that does not follow symlinks.
   * @throws IOException when the platform specific monitoring service cannot be initialized due to
   *     an io error
   * @throws InterruptedException when the platform specific monitoring service cannot initialize
   *     its background threads
   */
  NoFollowSymlinks<PathWatchers.Event> noFollowSymlinks() throws IOException, InterruptedException;

  /**
   * Create a PathWatcher that will follow symlinks and generate file events for the symlink when
   * its target is modified. The implementation will be platform dependent.
   *
   * @return a PathWatcher that does not follow symlinks.
   * @throws IOException when the platform specific monitoring service cannot be initialized due to
   *     an io error
   * @throws InterruptedException when the platform specific monitoring service cannot initialize
   *     its background threads
   */
  FollowSymlinks<PathWatchers.Event> followSymlinks() throws IOException, InterruptedException;

  /**
   * Create a path watcher that periodically polls the file system to detect changes. It will always
   * follow symlinks.
   *
   * @param pollInterval minimum duration between when polling ends and the next poll begins
   * @param timeUnit the time unit for which the pollInterval corresponds
   * @return the polling path watcher.
   * @throws InterruptedException if the polling thread cannot be started.
   */
  PathWatcher<Event> polling(final long pollInterval, final TimeUnit timeUnit)
      throws InterruptedException;
}
