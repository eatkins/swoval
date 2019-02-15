package com.swoval.files.impl;

import com.swoval.files.FileTreeRepositoryProvider;
import com.swoval.files.PathWatcherProvider;
import com.swoval.files.SwovalProvider;

public class SwovalProviderImpl implements SwovalProvider {
  private static final SwovalProvider impl = load();

  public static SwovalProvider getDefaultProvider() {
    return impl;
  }

  private static SwovalProvider load() {
    final SwovalProvider userDefined = SwovalProviderFactory.loadProvider();
    return userDefined != null ? userDefined : new Impl();
  }

  @Override
  public FileTreeRepositoryProvider getFileTreeRepositoryProvider() {
    return SwovalProviderImpl.impl.getFileTreeRepositoryProvider();
  }

  @Override
  public PathWatcherProvider getPathWatcherProvider() {
    return SwovalProviderImpl.impl.getPathWatcherProvider();
  }

  private static class Impl implements SwovalProvider {
    final FileTreeRepositoryProvider fileTreeRepositoryProvider =
        new FileTreeRepositoryProviderImpl();
    final PathWatcherProvider pathWatcherProvider = new PathWatcherProviderImpl();

    @Override
    public FileTreeRepositoryProvider getFileTreeRepositoryProvider() {
      return fileTreeRepositoryProvider;
    }

    @Override
    public PathWatcherProvider getPathWatcherProvider() {
      return pathWatcherProvider;
    }
  }
}
