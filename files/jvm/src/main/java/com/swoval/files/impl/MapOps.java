package com.swoval.files.impl;

import static java.util.Map.Entry;

import com.swoval.files.cache.CacheObserver;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Provides a utility method for diffing two maps of directory entries. It is not in {@link
 * CachedDirectoryImpl} because of a name class with java.util.Map.CacheEntry and
 * com.swoval.files.CachedDirectory.CacheEntry that breaks code-gen.
 */
class MapOps {
  private MapOps() {}

  static <T> void diffDirectoryEntries(
      final List<com.swoval.files.cache.Entry<T>> oldEntries,
      final List<com.swoval.files.cache.Entry<T>> newEntries,
      final CacheObserver<T> cacheObserver) {
    final Map<Path, com.swoval.files.cache.Entry<T>> oldMap = new HashMap<>();
    final Iterator<com.swoval.files.cache.Entry<T>> oldIterator = oldEntries.iterator();
    while (oldIterator.hasNext()) {
      final com.swoval.files.cache.Entry<T> cacheEntry = oldIterator.next();
      oldMap.put(cacheEntry.getTypedPath().getPath(), cacheEntry);
    }
    final Map<Path, com.swoval.files.cache.Entry<T>> newMap = new HashMap<>();
    final Iterator<com.swoval.files.cache.Entry<T>> newIterator = newEntries.iterator();
    while (newIterator.hasNext()) {
      final com.swoval.files.cache.Entry<T> cacheEntry = newIterator.next();
      newMap.put(cacheEntry.getTypedPath().getPath(), cacheEntry);
    }
    diffDirectoryEntries(oldMap, newMap, cacheObserver);
  }

  static <K, V> void diffDirectoryEntries(
      final Map<K, com.swoval.files.cache.Entry<V>> oldMap,
      final Map<K, com.swoval.files.cache.Entry<V>> newMap,
      final CacheObserver<V> cacheObserver) {
    final Iterator<java.util.Map.Entry<K, com.swoval.files.cache.Entry<V>>> newIterator =
        new ArrayList<>(newMap.entrySet()).iterator();
    final Iterator<Entry<K, com.swoval.files.cache.Entry<V>>> oldIterator =
        new ArrayList<>(oldMap.entrySet()).iterator();
    while (newIterator.hasNext()) {
      final Entry<K, com.swoval.files.cache.Entry<V>> entry = newIterator.next();
      final com.swoval.files.cache.Entry<V> oldValue = oldMap.get(entry.getKey());
      if (oldValue != null) {
        cacheObserver.onUpdate(oldValue, entry.getValue());
      } else {
        cacheObserver.onCreate(entry.getValue());
      }
    }
    while (oldIterator.hasNext()) {
      final Entry<K, com.swoval.files.cache.Entry<V>> entry = oldIterator.next();
      if (!newMap.containsKey(entry.getKey())) {
        cacheObserver.onDelete(entry.getValue());
      }
    }
  }
}
