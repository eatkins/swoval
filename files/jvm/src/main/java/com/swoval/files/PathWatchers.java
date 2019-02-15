package com.swoval.files;

import com.swoval.logging.Logger;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

// Ignore the errors in javadoc in intellij. It is getting confused by having the java and
// js implementations.
/**
 * Provides factory methods to create instances of {@link com.swoval.files.PathWatcher}. It also
 * defines the {@link com.swoval.files.PathWatchers.Event} class for which the {@link
 * com.swoval.files.PathWatcher} will emit events.
 */
public class PathWatchers {
  private PathWatchers() {}

  private static final PathWatcherProvider provider =
      SwovalProviderImpl.getDefaultProvider().getPathWatcherProvider();

  public static FollowSymlinks<PathWatchers.Event> followSymlinks(final Logger logger)
      throws IOException, InterruptedException {
    return provider.followSymlinks(logger);
  }

  public static NoFollowSymlinks<PathWatchers.Event> noFollowSymlinks(final Logger logger)
      throws IOException, InterruptedException {
    return provider.noFollowSymlinks(logger);
  }

  public static PathWatcher<PathWatchers.Event> polling(
      final long duration, final TimeUnit timeUnit, final Logger logger)
      throws InterruptedException {
    return provider.polling(duration, timeUnit, logger);
  }

  /** Container for {@link PathWatcher} events. */
  public static final class Event {
    private final TypedPath typedPath;
    private final Event.Kind kind;

    /**
     * Return the {@link TypedPath} associated with this Event.
     *
     * @return the {@link TypedPath}.
     */
    public TypedPath getTypedPath() {
      return typedPath;
    }

    /**
     * Returns the kind of event.
     *
     * @return the kind of event.
     */
    public Kind getKind() {
      return kind;
    }

    public Event(final TypedPath typedPath, final Event.Kind kind) {
      this.typedPath = typedPath;
      this.kind = kind;
    }

    @Override
    public boolean equals(Object other) {
      if (other instanceof Event) {
        Event that = (Event) other;
        return this.typedPath.equals(that.typedPath) && this.kind.equals(that.kind);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return typedPath.hashCode() ^ kind.hashCode();
    }

    @Override
    public String toString() {
      return "Event(" + typedPath.getPath() + ", " + kind + ")";
    }

    /**
     * An enum like class to indicate the type of file event. It isn't an actual enum because the
     * scala.js codegen has problems with enum types.
     */
    public static class Kind {

      /** A new file was created. */
      public static final Kind Create = new Kind("Create");
      /** The file was deleted. */
      public static final Kind Delete = new Kind("Delete");
      /** An error occurred processing the event. */
      public static final Kind Error = new Kind("Error");
      /** An existing file was modified. */
      public static final Kind Modify = new Kind("Modify");
      /** The watching service overflowed so it may be necessary to poll. */
      public static final Kind Overflow = new Kind("Overflow");

      private final String name;

      Kind(final String name) {
        this.name = name;
      }

      @Override
      public String toString() {
        return name;
      }

      @Override
      public boolean equals(final Object other) {
        return other instanceof Kind && ((Kind) other).name.equals(this.name);
      }

      @Override
      public int hashCode() {
        return name.hashCode();
      }
    }
  }

  public interface FollowSymlinks<T> extends PathWatcher<T> {}

  public interface NoFollowSymlinks<T> extends PathWatcher<T> {}
}
