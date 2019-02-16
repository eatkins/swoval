package com.swoval.files.impl;

import com.swoval.files.FileTreeRepositoryProvider;
import com.swoval.files.FileTreeViewProvider;
import com.swoval.files.PathWatcherProvider;
import com.swoval.files.SwovalProvider;

public class SwovalProviderImpl {

  private static SwovalProvider impl = load();

  public static SwovalProvider getDefaultProvider() {
    return impl;
  }

  private static SwovalProvider load() {
    final SwovalProvider userDefined = SwovalProviderFactory.loadProvider();
    return userDefined != null ? userDefined : new Impl();
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
    public FileTreeViewProvider getFileTreeViewProvider() {
      return new com.swoval.files.impl.FileTreeViewProvider();
    }

    @Override
    public PathWatcherProvider getPathWatcherProvider() {
      return pathWatcherProvider;
    }
  }
}
