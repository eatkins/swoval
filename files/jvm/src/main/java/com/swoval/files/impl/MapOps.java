package com.swoval.files.impl;

import static java.util.Map.Entry;

import com.swoval.files.CacheEntry;
import com.swoval.files.FileTreeDataViews.CacheObserver;
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
      final List<CacheEntry<T>> oldEntries,
      final List<CacheEntry<T>> newEntries,
      final CacheObserver<T> cacheObserver) {
    final Map<Path, CacheEntry<T>> oldMap = new HashMap<>();
    final Iterator<CacheEntry<T>> oldIterator = oldEntries.iterator();
    while (oldIterator.hasNext()) {
      final CacheEntry<T> cacheEntry = oldIterator.next();
      oldMap.put(cacheEntry.getTypedPath().getPath(), cacheEntry);
    }
    final Map<Path, CacheEntry<T>> newMap = new HashMap<>();
    final Iterator<CacheEntry<T>> newIterator = newEntries.iterator();
    while (newIterator.hasNext()) {
      final CacheEntry<T> cacheEntry = newIterator.next();
      newMap.put(cacheEntry.getTypedPath().getPath(), cacheEntry);
    }
    diffDirectoryEntries(oldMap, newMap, cacheObserver);
  }

  static <K, V> void diffDirectoryEntries(
      final Map<K, CacheEntry<V>> oldMap,
      final Map<K, CacheEntry<V>> newMap,
      final CacheObserver<V> cacheObserver) {
    final Iterator<Entry<K, CacheEntry<V>>> newIterator =
        new ArrayList<>(newMap.entrySet()).iterator();
    final Iterator<Entry<K, CacheEntry<V>>> oldIterator =
        new ArrayList<>(oldMap.entrySet()).iterator();
    while (newIterator.hasNext()) {
      final Entry<K, CacheEntry<V>> entry = newIterator.next();
      final CacheEntry<V> oldValue = oldMap.get(entry.getKey());
      if (oldValue != null) {
        cacheObserver.onUpdate(oldValue, entry.getValue());
      } else {
        cacheObserver.onCreate(entry.getValue());
      }
    }
    while (oldIterator.hasNext()) {
      final Entry<K, CacheEntry<V>> entry = oldIterator.next();
      if (!newMap.containsKey(entry.getKey())) {
        cacheObserver.onDelete(entry.getValue());
      }
    }
  }
}
