package com.swoval.files.impl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

class DirectoryListers {
  private DirectoryListers() {}

  static final DirectoryLister INSTANCE = init();

  @SuppressWarnings({"unchecked", "EmptyCatchBlock"})
  public static DirectoryLister init() {
    final String className = System.getProperty("swoval.directory.lister");
    if (className != null) {
      try {
        Constructor<DirectoryLister> cons =
            ((Class<DirectoryLister>) Class.forName(className)).getDeclaredConstructor();
        cons.setAccessible(true);
        return cons.newInstance();
      } catch (ClassNotFoundException
          | NoSuchMethodException
          | ClassCastException
          | IllegalAccessException
          | InstantiationException
          | InvocationTargetException e) {
      }
    }
    try {
      return new NativeDirectoryLister();
    } catch (final UnsatisfiedLinkError | RuntimeException e) {
      return new NioDirectoryLister();
    }
  }
}
