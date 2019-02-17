package com.swoval.files.impl.apple;

import com.swoval.files.PathWatchers;
import com.swoval.files.api.PathWatcher;
import com.swoval.files.impl.DirectoryRegistry;
import com.swoval.logging.Logger;
import java.util.concurrent.TimeUnit;

public class ApplePathWatchers {
  private ApplePathWatchers() {}

  public static PathWatcher<PathWatchers.Event> get(
      final DirectoryRegistry registry, final Logger logger) throws InterruptedException {
    return new com.swoval.files.impl.apple.ApplePathWatcher(
        10,
        TimeUnit.MILLISECONDS,
        new Flags.Create().setNoDefer().setFileEvents(),
        com.swoval.files.impl.apple.ApplePathWatcher.DefaultOnStreamRemoved,
        registry,
        logger);
  }
}
