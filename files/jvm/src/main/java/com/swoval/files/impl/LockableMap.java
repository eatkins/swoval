package com.swoval.files.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;

public class LockableMap<K, V> extends Lockable {
  private final Map<K, V> map;

  public LockableMap(final Map<K, V> map, final ReentrantLock reentrantLock) {
    super(reentrantLock);
    this.map = map;
  }

  public LockableMap(final Map<K, V> map) {
    this(map, new ReentrantLock());
  }

  public LockableMap() {
    this(new HashMap<K, V>(), new ReentrantLock());
  }

  @SuppressWarnings("EmptyCatchBlock")
  public void clear() {
    if (lock()) {
      try {
        final Iterator<V> values = new ArrayList<>(map.values()).iterator();
        while (values.hasNext()) {
          try {
            final V v = values.next();
            if (v instanceof AutoCloseable) ((AutoCloseable) v).close();
          } catch (final Exception e) {
          }
        }
        map.clear();
      } finally {
        unlock();
      }
    }
  }

  public Iterator<Entry<K, V>> iterator() {
    if (lock()) {
      try {
        return new ArrayList<>(map.entrySet()).iterator();
      } finally {
        unlock();
      }
    } else {
      return Collections.emptyListIterator();
    }
  }

  public List<K> keys() {
    if (lock()) {
      try {
        return new ArrayList<>(map.keySet());
      } finally {
        unlock();
      }
    } else {
      return Collections.emptyList();
    }
  }

  public List<V> values() {
    if (lock()) {
      try {
        return new ArrayList<>(map.values());
      } finally {
        unlock();
      }
    } else {
      return Collections.emptyList();
    }
  }

  public V get(final K key) {
    if (lock()) {
      try {
        return map.get(key);
      } finally {
        unlock();
      }
    } else {
      return (V) null;
    }
  }

  public V put(final K key, V value) {
    if (lock()) {
      try {
        return map.put(key, value);
      } finally {
        unlock();
      }
    } else {
      return (V) null;
    }
  }

  public V remove(final K key) {
    if (lock()) {
      try {
        return map.remove(key);
      } finally {
        unlock();
      }
    } else {
      return (V) null;
    }
  }

  @Override
  public String toString() {
    return "LockableMap(" + map + ")";
  }
}
