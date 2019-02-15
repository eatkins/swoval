package com.swoval.files.impl;

import com.swoval.files.FileTreeDataViews.CacheObserver;
import com.swoval.files.FileTreeViews.Observer;
import com.swoval.files.PathWatcher;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;
import com.swoval.logging.Loggers.Level;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Container class that wraps multiple {@link Observer} and runs the callbacks for each whenever the
 * {@link PathWatcher} detects an event.
 *
 * @param <T> the data type for the {@link PathWatcher} to which the observers correspond
 */
class Observers<T> implements Observer<T>, AutoCloseable {
  private final AtomicInteger counter = new AtomicInteger(0);
  private final Map<Integer, Observer<T>> observers = new LinkedHashMap<>();
  private final Logger logger;

  Observers(final Logger logger) {
    this.logger = logger;
  }

  @Override
  public void onNext(final T t) {
    final List<Observer<T>> cbs;
    synchronized (observers) {
      cbs = new ArrayList<>(observers.values());
    }
    final Iterator<Observer<T>> it = cbs.iterator();
    while (it.hasNext()) {
      try {
        it.next().onNext(t);
      } catch (final Exception e) {
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          Loggers.logException(logger, e);
        }
      }
    }
  }

  @Override
  public void onError(final Throwable throwable) {
    final List<Observer<T>> cbs;
    synchronized (observers) {
      cbs = new ArrayList<>(observers.values());
    }
    final Iterator<Observer<T>> it = cbs.iterator();
    while (it.hasNext()) {
      try {
        it.next().onError(throwable);
      } catch (final Exception e) {
        if (Loggers.shouldLog(logger, Level.ERROR)) {
          Loggers.logException(logger, e);
        }
      }
    }
  }

  /**
   * Add an cacheObserver to receive events.
   *
   * @param observer the new cacheObserver
   * @return a handle to the added cacheObserver that can be used to halt observation using {@link
   *     Observers#removeObserver(int)} .
   */
  @SuppressWarnings("unchecked")
  int addObserver(final Observer<? super T> observer) {
    final int key = counter.getAndIncrement();
    synchronized (observers) {
      observers.put(key, (Observer<T>) observer);
    }
    return key;
  }

  /**
   * Remove an instance of {@link CacheObserver} that was previously added using {@link
   * Observers#addObserver(Observer)}.
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
}
