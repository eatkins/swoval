package com.swoval.files.impl;

import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.files.FileTreeDataViews.Entry;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class CacheUpdates<T> implements CacheObserver<T> {

  private final List<Entry<T>> creations = new ArrayList<>();
  private final List<Entry<T>> deletions = new ArrayList<>();
  private final List<Entry<T>[]> updates = new ArrayList<>();

  void observe(final CacheObserver<T> cacheObserver) {
    final Iterator<Entry<T>> creationIterator = creations.iterator();
    while (creationIterator.hasNext()) {
      cacheObserver.onCreate(creationIterator.next());
    }
    final Iterator<Entry<T>[]> updateIterator = updates.iterator();
    while (updateIterator.hasNext()) {
      final Entry<T>[] entries = updateIterator.next();
      cacheObserver.onUpdate(entries[0], entries[1]);
    }
    final Iterator<Entry<T>> deletionIterator = deletions.iterator();
    while (deletionIterator.hasNext()) {
      cacheObserver.onDelete(Entries.setExists(deletionIterator.next(), false));
    }
  }

  @Override
  public void onCreate(final Entry<T> newEntry) {
    creations.add(newEntry);
  }

  @Override
  public void onDelete(final Entry<T> oldEntry) {
    deletions.add(oldEntry);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onUpdate(final Entry<T> oldEntry, final Entry<T> newEntry) {
    updates.add(new Entry[] {oldEntry, newEntry});
  }

  @Override
  public void onError(final IOException exception) {}

  @Override
  public String toString() {
    final List<List<Entry<T>>> updateList = new ArrayList<>();
    final Iterator<Entry<T>[]> it = updates.iterator();
    while (it.hasNext()) updateList.add(Arrays.asList(it.next()));
    return "CacheUpdates("
        + ("creations: " + creations)
        + (", deletions: " + deletions)
        + (", updates: " + updateList + ")");
  }
}
