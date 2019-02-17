package com.swoval.files.cache;

import com.swoval.files.impl.CacheObservers;

public class Observers {
  public static <T> com.swoval.files.api.Observer<Event<T>> toEventObserver(
      final CacheObserver<T> cacheObserver) {
    return CacheObservers.fromCacheObserver(cacheObserver);
  }
}
