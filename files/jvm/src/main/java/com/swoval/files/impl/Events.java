package com.swoval.files.impl;

import com.swoval.files.cache.Creation;
import com.swoval.files.cache.Deletion;
import com.swoval.files.cache.Entry;
import com.swoval.files.cache.Event;
import com.swoval.files.cache.Update;
import java.util.Objects;

class Events {
  static <T> Event<T> creation(final Entry<T> entry) {
    return new EventImpl<>(new CreationImpl<>(entry), null, null, null);
  }

  static <T> Event<T> deletion(final Entry<T> deletion) {
    return new EventImpl<>(null, new DeletionImpl<>(deletion), null, null);
  }

  static <T> Event<T> update(final Entry<T> previous, final Entry<T> current) {
    return new EventImpl<>(null, null, new UpdateImpl<>(previous, current), null);
  }

  static <T> Event<T> error(final Throwable throwable) {
    return new EventImpl<>(null, null, null, throwable);
  }

  private static class CreationOrDeletionImpl<T> {
    private final Entry<T> entry;

    CreationOrDeletionImpl(final Entry<T> entry) {
      this.entry = entry;
    }

    public Entry<T> getEntry() {
      return entry;
    }

    @Override
    public int hashCode() {
      return entry.hashCode();
    }
  }

  private static class CreationImpl<T> extends CreationOrDeletionImpl<T> implements Creation<T> {
    CreationImpl(final Entry<T> entry) {
      super(entry);
    }

    @Override
    public boolean equals(final Object other) {
      return (other instanceof CreationImpl) && ((CreationImpl) other).getEntry() == getEntry();
    }
  }

  private static class DeletionImpl<T> extends CreationOrDeletionImpl<T> implements Deletion<T> {
    DeletionImpl(final Entry<T> entry) {
      super(entry);
    }

    @Override
    public boolean equals(final Object other) {
      return (other instanceof CreationImpl) && ((CreationImpl) other).getEntry() == getEntry();
    }
  }

  private static class UpdateImpl<T> implements Update<T> {
    private final Entry<T> previousEntry;
    private final Entry<T> currentEntry;

    UpdateImpl(final Entry<T> previousEntry, final Entry<T> currentEntry) {
      this.previousEntry = previousEntry;
      this.currentEntry = currentEntry;
    }

    @Override
    public Entry<T> getPreviousEntry() {
      return previousEntry;
    }

    @Override
    public Entry<T> getCurrentEntry() {
      return currentEntry;
    }

    @Override
    public int hashCode() {
      return currentEntry.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
      if (other instanceof UpdateImpl) {
        final UpdateImpl<?> that = (UpdateImpl<?>) other;
        return Objects.equals(that.previousEntry, previousEntry)
            && Objects.equals(that.currentEntry, currentEntry);
      } else {
        return false;
      }
    }
  }

  private static class EventImpl<T> implements Event<T> {
    private final Creation<T> creation;
    private final Deletion<T> deletion;
    private final Update<T> update;
    private final Throwable throwable;

    EventImpl(
        final Creation<T> creation,
        final Deletion<T> deletion,
        final Update<T> update,
        final Throwable throwable) {
      this.creation = creation;
      this.deletion = deletion;
      this.update = update;
      this.throwable = throwable;
    }

    @Override
    public Creation<T> getCreation() {
      return creation;
    }

    @Override
    public Deletion<T> getDeletion() {
      return deletion;
    }

    @Override
    public Update<T> getUpdate() {
      return update;
    }

    @Override
    public Throwable getThrowable() {
      return throwable;
    }

    @Override
    public String toString() {
      return creation != null
          ? creation.toString()
          : deletion != null
              ? deletion.toString()
              : update != null ? update.toString() : throwable.toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(final Object other) {
      if (other instanceof EventImpl) {
        final EventImpl<T> that = (EventImpl<T>) other;
        return Objects.equals(this.creation, that.creation)
            && Objects.equals(this.deletion, that.deletion)
            && Objects.equals(this.update, that.update)
            && Objects.equals(this.throwable, that.throwable);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return creation != null
          ? creation.hashCode()
          : deletion != null
              ? deletion.hashCode()
              : update != null ? update.hashCode() : throwable.hashCode();
    }
  }
}
