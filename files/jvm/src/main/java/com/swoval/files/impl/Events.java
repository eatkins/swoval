package com.swoval.files.impl;

import com.swoval.files.cache.Creation;
import com.swoval.files.cache.Deletion;
import com.swoval.files.cache.Event;
import com.swoval.files.cache.Update;
import java.util.Objects;

class Events {
  static <T> Event<T> creation(final Creation<T> creation) {
    return new EventImpl<>(creation, null, null, null);
  }

  static <T> Event<T> deletion(final Deletion<T> deletion) {
    return new EventImpl<>(null, deletion, null, null);
  }

  static <T> Event<T> update(final Update<T> update) {
    return new EventImpl<>(null, null, update, null);
  }

  static <T> Event<T> error(final Throwable throwable) {
    return new EventImpl<T>(null, null, null, throwable);
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
