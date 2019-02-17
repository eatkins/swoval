package com.swoval.files.impl;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

class Lockable {
  private final ReentrantLock reentrantLock;

  Lockable(final ReentrantLock reentrantLock) {
    this.reentrantLock = reentrantLock;
  }

  public boolean lock() {
    try {
      return reentrantLock.tryLock(1, TimeUnit.MINUTES);
    } catch (final InterruptedException e) {
      return false;
    }
  }

  public void unlock() {
    reentrantLock.unlock();
  }
}
