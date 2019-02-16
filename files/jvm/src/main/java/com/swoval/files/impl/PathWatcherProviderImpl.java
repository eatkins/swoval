package com.swoval.files.impl;

import com.swoval.files.Observer;
import com.swoval.files.PathWatcher;
import com.swoval.files.PathWatcherProvider;
import com.swoval.files.PathWatchers.Event;
import com.swoval.files.PathWatchers.FollowSymlinks;
import com.swoval.files.PathWatchers.NoFollowSymlinks;
import com.swoval.files.impl.apple.ApplePathWatchers;
import com.swoval.functional.Either;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.runtime.Platform;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

class PathWatcherProviderImpl implements PathWatcherProvider {

  @Override
  public NoFollowSymlinks<Event> noFollowSymlinks() throws IOException, InterruptedException {
    final DirectoryRegistry registry = new DirectoryRegistryImpl();
    return get(registry, Loggers.getLogger());
  }

  @Override
  public FollowSymlinks<Event> followSymlinks() throws IOException, InterruptedException {
    final DirectoryRegistry registry = new DirectoryRegistryImpl();
    final Logger logger = Loggers.getLogger();
    return new SymlinkFollowingPathWatcherImpl(get(registry, logger), registry, logger, this);
  }

  @Override
  public PathWatcher<Event> polling(long pollInterval, TimeUnit timeUnit)
      throws InterruptedException {
    return new FollowWrapper<>(
        new PollingPathWatcher(true, pollInterval, timeUnit, Loggers.getLogger()));
  }

  static NoFollowSymlinks<Event> get(final DirectoryRegistry registry, final Logger logger)
      throws InterruptedException, IOException {
    return new NoFollowWrapper<>(
        Platform.isMac()
            ? ApplePathWatchers.get(registry, logger)
            : PlatformWatcher.make(registry, logger));
  }

  private static class Wrapper<T> implements PathWatcher<T> {
    private final PathWatcher<T> delegate;

    Wrapper(final PathWatcher<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public Either<IOException, Boolean> register(final Path path, final int maxDepth) {
      return delegate.register(path, maxDepth);
    }

    @Override
    public void unregister(final Path path) {
      delegate.unregister(path);
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public int addObserver(final Observer<? super T> observer) {
      return delegate.addObserver(observer);
    }

    @Override
    public void removeObserver(final int handle) {
      delegate.removeObserver(handle);
    }
  }

  static final class NoFollowWrapper<T> extends Wrapper<T> implements NoFollowSymlinks<T> {
    NoFollowWrapper(final PathWatcher<T> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      return "NoFollowSymlinksPathWatcher@" + System.identityHashCode(this);
    }
  }

  static final class FollowWrapper<T> extends Wrapper<T> implements FollowSymlinks<T> {
    FollowWrapper(final PathWatcher<T> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      return "SymlinkFollowingPathWatcher@" + System.identityHashCode(this);
    }
  }
}
