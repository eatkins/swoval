package com.swoval.files.impl;

import com.swoval.files.api.Observer;
import com.swoval.files.cache.CacheObserver;
import com.swoval.files.cache.Creation;
import com.swoval.files.cache.Deletion;
import com.swoval.files.cache.Entry;
import com.swoval.files.cache.Event;
import com.swoval.files.cache.Update;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CacheObservers<T> implements CacheObserver<T>, AutoCloseable {
  private final AtomicInteger counter = new AtomicInteger(0);
  private final Map<Integer, CacheObserver<T>> observers = new LinkedHashMap<>();
  private final Logger logger;

  CacheObservers(final Logger logger) {
    this.logger = logger;
  }

  @Override
  public void onCreate(final Entry<T> newCacheEntry) {
    final List<CacheObserver<T>> cbs;
    synchronized (observers) {
      cbs = new ArrayList<>(observers.values());
    }
    final Iterator<CacheObserver<T>> it = cbs.iterator();
    while (it.hasNext()) {
      try {
        it.next().onCreate(newCacheEntry);
      } catch (final Exception e) {
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          Loggers.logException(logger, e);
        }
      }
    }
  }

  @Override
  public void onDelete(final Entry<T> oldCacheEntry) {
    final List<CacheObserver<T>> cbs;
    synchronized (observers) {
      cbs = new ArrayList<>(observers.values());
    }
    final Iterator<CacheObserver<T>> it = cbs.iterator();
    while (it.hasNext()) {
      try {
        it.next().onDelete(oldCacheEntry);
      } catch (final Exception e) {
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          Loggers.logException(logger, e);
        }
      }
    }
  }

  @Override
  public void onUpdate(final Entry<T> oldCacheEntry, final Entry<T> newCacheEntry) {
    final List<CacheObserver<T>> cbs;
    synchronized (observers) {
      cbs = new ArrayList<>(observers.values());
    }
    final Iterator<CacheObserver<T>> it = cbs.iterator();
    while (it.hasNext()) {
      try {
        it.next().onUpdate(oldCacheEntry, newCacheEntry);
      } catch (final Exception e) {
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          Loggers.logException(logger, e);
        }
      }
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    final List<CacheObserver<T>> cbs;
    synchronized (observers) {
      cbs = new ArrayList<>(observers.values());
    }
    final Iterator<CacheObserver<T>> it = cbs.iterator();
    while (it.hasNext()) it.next().onError(throwable);
  }

  /**
   * Add an cacheObserver to receive events.
   *
   * @param observer the new cacheObserver
   * @return a handle to the added cacheObserver that can be used to halt observation using {@link
   *     com.swoval.files.Observers#removeObserver(int)} .
   */
  int addObserver(final Observer<? super Entry<T>> observer) {
    final int key = counter.getAndIncrement();
    synchronized (observers) {
      observers.put(key, CacheObservers.fromObserver(observer));
    }
    return key;
  }

  int addCacheObserver(final CacheObserver<T> cacheObserver) {
    final int key = counter.getAndIncrement();
    synchronized (observers) {
      observers.put(key, cacheObserver);
    }
    return key;
  }

  /**
   * Remove an instance of {@link CacheObserver} that was previously added using {@link
   * com.swoval.files.Observers#addObserver(Observer)}.
   *
   * @param handle the handle to remove
   */
  void removeObserver(int handle) {
    synchronized (observers) {
      observers.remove(handle);
    }
  }

  @Override
  public void close() {
    observers.clear();
  }

  private static class WrappedObserver<T> implements CacheObserver<T> {
    private final Observer<? super Entry<T>> observer;

    WrappedObserver(final Observer<? super Entry<T>> observer) {
      this.observer = observer;
    }

    @Override
    public void onCreate(final Entry<T> newCacheEntry) {
      observer.onNext(newCacheEntry);
    }

    @Override
    public void onDelete(final Entry<T> oldCacheEntry) {
      observer.onNext(oldCacheEntry);
    }

    @Override
    public void onUpdate(final Entry<T> oldCacheEntry, final Entry<T> newCacheEntry) {
      observer.onNext(newCacheEntry);
    }

    @Override
    public void onError(final Throwable throwable) {
      observer.onError(throwable);
    }

    @Override
    public String toString() {
      return "WrappedCacheObserver(" + observer + ")";
    }

    @Override
    public int hashCode() {
      return observer.hashCode();
    }

    @Override
    public boolean equals(final Object other) {
      return (other instanceof WrappedObserver<?>)
          && ((WrappedObserver) other).observer.equals(this.observer);
    }
  }

  private static class WrappedCacheObserver<T> implements Observer<Event<T>> {
    private final CacheObserver<T> cacheObserver;

    WrappedCacheObserver(final CacheObserver<T> cacheObserver) {
      this.cacheObserver = cacheObserver;
    }

    @Override
    public void onError(final Throwable t) {
      cacheObserver.onError(t);
    }

    @Override
    public void onNext(final Event<T> event) {
      onNextImpl(cacheObserver, event);
    }

    @Override
    public String toString() {
      return "WrappedCacheObserver(" + cacheObserver + ")";
    }

    @Override
    public boolean equals(final Object other) {
      return (other instanceof WrappedCacheObserver<?>)
          && ((WrappedCacheObserver) other).cacheObserver.equals(this.cacheObserver);
    }

    @Override
    public int hashCode() {
      return cacheObserver.hashCode();
    }
  }

  static <T> CacheObserver<T> fromObserver(final Observer<? super Entry<T>> observer) {
    return new WrappedObserver<>(observer);
  }

  public static <T> Observer<Event<T>> fromCacheObserver(final CacheObserver<T> cacheObserver) {
    return new WrappedCacheObserver<>(cacheObserver);
  }

  private static <T> void onNextImpl(final CacheObserver<T> cacheObserver, final Event<T> event) {
    final Creation<T> creation = event.getCreation();
    final Deletion<T> deletion = event.getDeletion();
    final Update<T> update = event.getUpdate();
    final Throwable throwable = event.getThrowable();
    if (creation != null) {
      cacheObserver.onCreate(creation.getEntry());
    } else if (deletion != null) {
      cacheObserver.onDelete(deletion.getEntry());
    } else if (update != null) {
      cacheObserver.onUpdate(update.getPreviousEntry(), update.getCurrentEntry());
    } else if (throwable != null) {
      cacheObserver.onError(throwable);
    }
  }
}
