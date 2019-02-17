package com.swoval.files.cache;

public interface Event<T> {
  Creation<T> getCreation();

  Deletion<T> getDeletion();

  Update<T> getUpdate();

  Throwable getThrowable();
}
