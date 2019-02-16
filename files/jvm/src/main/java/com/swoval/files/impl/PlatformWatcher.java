package com.swoval.files.impl;

import com.swoval.files.api.PathWatcher;
import com.swoval.files.PathWatchers.Event;
import com.swoval.logging.Logger;
import java.io.IOException;

class PlatformWatcher {
  static PathWatcher<Event> make(final DirectoryRegistry directoryRegistry, final Logger logger)
      throws InterruptedException, IOException {
    return make(RegisterableWatchServices.get(), directoryRegistry, logger);
  }

  static PathWatcher<Event> make(
      final RegisterableWatchService registerableWatchService,
      final DirectoryRegistry directoryRegistry,
      final Logger logger)
      throws InterruptedException {
    return new NioPathWatcher(directoryRegistry, registerableWatchService, logger);
  }
}
