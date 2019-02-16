package com.swoval.files.impl;

import com.swoval.files.FileTreeRepositoryProvider;
import com.swoval.files.FileTreeViewProvider;
import com.swoval.files.PathWatcherProvider;
import com.swoval.files.SwovalProvider;
import com.swoval.logging.Logger;
import com.swoval.logging.Loggers;

public class SwovalProviderImpl {

  private static SwovalProvider impl = load();

  public static SwovalProvider getDefaultProvider() {
    return impl;
  }

  private static SwovalProvider load() {
    final SwovalProvider userDefined = SwovalProviderFactory.loadProvider();
    return userDefined != null ? userDefined : new Impl(Loggers.getLogger());
  }

  private static class Impl implements SwovalProvider {
    final FileTreeRepositoryProvider fileTreeRepositoryProvider;
    final PathWatcherProvider pathWatcherProvider;

    Impl(final Logger logger) {
      fileTreeRepositoryProvider = new FileTreeRepositoryProviderImpl(logger);
      pathWatcherProvider = new PathWatcherProviderImpl();
    }

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
