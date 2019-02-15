package com.swoval.files;

import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.files.FileTreeViews.Observer;
import com.swoval.files.PathWatchers.Event;
import com.swoval.files.PathWatchers.FollowSymlinks;
import com.swoval.files.PathWatchers.NoFollowSymlinks;
import com.swoval.functional.Either;
import com.swoval.logging.Logger;
import com.swoval.runtime.Platform;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

class PathWatcherProviderImpl implements PathWatcherProvider {

  @Override
  public NoFollowSymlinks<PathWatchers.Event> noFollowSymlinks(Logger logger)
      throws IOException, InterruptedException {
    final DirectoryRegistry registry = new DirectoryRegistryImpl();
    return get(registry, logger);
  }

  @Override
  public FollowSymlinks<PathWatchers.Event> followSymlinks(Logger logger)
      throws IOException, InterruptedException {
    final DirectoryRegistry registry = new DirectoryRegistryImpl();
    return new SymlinkFollowingPathWatcherImpl(get(registry, logger), registry, logger, this);
  }

  @Override
  public PathWatcher<Event> polling(long pollInterval, TimeUnit timeUnit, Logger logger)
      throws InterruptedException {
    return new PollingPathWatcher(true, pollInterval, timeUnit, logger);
  }

  private static NoFollowSymlinks<PathWatchers.Event> get(
      final DirectoryRegistry registry, final Logger logger)
      throws InterruptedException, IOException {
    return new NoFollowWrapper<>(
        Platform.isMac()
            ? ApplePathWatchers.get(registry, logger)
            : PlatformWatcher.make(registry, logger));
  }

  private static <T> PathWatcher<T> get(
      final Converter<T> converter, final DirectoryRegistry registry, final Logger logger)
      throws InterruptedException, IOException {
    return new ConvertedPathWatcher<T>(get(registry, logger), converter, logger);
  }

  private static class ConvertedPathWatcher<T> implements PathWatcher<T> {
    private final PathWatcher<Event> pathWatcher;
    private final Observers<T> observers;
    private final Converter<T> converter;
    private final int handle;

    ConvertedPathWatcher(
        final PathWatcher<Event> pathWatcher, final Converter<T> converter, final Logger logger) {
      this.pathWatcher = pathWatcher;
      this.converter = converter;
      this.observers = new Observers<>(logger);
      this.handle =
          pathWatcher.addObserver(
              new Observer<Event>() {
                @Override
                public void onError(final Throwable t) {
                  observers.onError(t);
                }

                @Override
                public void onNext(Event event) {
                  observe(event);
                }
              });
    }

    @Override
    public Either<IOException, Boolean> register(Path path, int maxDepth) {
      return pathWatcher.register(path, maxDepth);
    }

    @Override
    public void unregister(Path path) {
      pathWatcher.unregister(path);
    }

    @Override
    public void close() {
      pathWatcher.removeObserver(this.handle);
      observers.close();
      pathWatcher.close();
    }

    public int addObserver(Observer<? super T> observer) {
      return observers.addObserver(observer);
    }

    @Override
    public void removeObserver(int handle) {
      observers.removeObserver(handle);
    }

    private void observe(final Event event) {
      try {
        observers.onNext(converter.apply(event.getTypedPath()));
      } catch (final IOException e) {
        observers.onError(e);
      }
    }
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

  private static final class NoFollowWrapper<T> extends Wrapper<T> implements NoFollowSymlinks<T> {
    NoFollowWrapper(final PathWatcher<T> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      return "NoFollowSymlinksPathWatcher@" + System.identityHashCode(this);
    }
  }

  private static final class FollowWrapper<T> extends Wrapper<T> implements FollowSymlinks<T> {
    FollowWrapper(final PathWatcher<T> delegate) {
      super(delegate);
    }

    @Override
    public String toString() {
      return "SymlinkFollowingPathWatcher@" + System.identityHashCode(this);
    }
  }
}
