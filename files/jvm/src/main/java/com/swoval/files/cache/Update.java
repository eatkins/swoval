package com.swoval.files.cache;

public interface Update<T> {
  Entry<T> getPreviousEntry();

  Entry<T> getCurrentEntry();
}
