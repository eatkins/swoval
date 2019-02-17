package com.swoval.files.impl;

import com.swoval.files.CacheEntry;
import com.swoval.files.FileTreeDataViews.CacheObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

class CacheUpdates<T> implements CacheObserver<T> {

  private final List<CacheEntry<T>> creations = new ArrayList<>();
  private final List<CacheEntry<T>> deletions = new ArrayList<>();
  private final List<CacheEntry<T>[]> updates = new ArrayList<>();

  void observe(final CacheObserver<T> cacheObserver) {
    final Iterator<CacheEntry<T>> creationIterator = creations.iterator();
    while (creationIterator.hasNext()) {
      cacheObserver.onCreate(creationIterator.next());
    }
    final Iterator<CacheEntry<T>[]> updateIterator = updates.iterator();
    while (updateIterator.hasNext()) {
      final CacheEntry<T>[] entries = updateIterator.next();
      cacheObserver.onUpdate(entries[0], entries[1]);
    }
    final Iterator<CacheEntry<T>> deletionIterator = deletions.iterator();
    while (deletionIterator.hasNext()) {
      cacheObserver.onDelete(Entries.setExists(deletionIterator.next(), false));
    }
  }

  @Override
  public void onCreate(final CacheEntry<T> newCacheEntry) {
    creations.add(newCacheEntry);
  }

  @Override
  public void onDelete(final CacheEntry<T> oldCacheEntry) {
    deletions.add(oldCacheEntry);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onUpdate(final CacheEntry<T> oldCacheEntry, final CacheEntry<T> newCacheEntry) {
    updates.add(new CacheEntry[] {oldCacheEntry, newCacheEntry});
  }

  @Override
  public void onError(final IOException exception) {}

  @Override
  public String toString() {
    final List<List<CacheEntry<T>>> updateList = new ArrayList<>();
    final Iterator<CacheEntry<T>[]> it = updates.iterator();
    while (it.hasNext()) updateList.add(Arrays.asList(it.next()));
    return "CacheUpdates("
        + ("creations: " + creations)
        + (", deletions: " + deletions)
        + (", updates: " + updateList + ")");
  }
}
