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

  public static SwovalProvider getRelativeProvider() {
    return impl;
  }

  private static SwovalProvider load() {
    final SwovalProvider userDefined = SwovalProviderFactory.loadProvider();
    return userDefined != null ? userDefined : Impl.get(Loggers.getLogger());
  }

  private static class Impl implements SwovalProvider {
    final FileTreeRepositoryProvider fileTreeRepositoryProvider;
    final FileTreeViewProvider fileTreeViewProvider;
    final PathWatcherProvider pathWatcherProvider;

    Impl(
        final FileTreeRepositoryProvider fileTreeRepositoryProvider,
        final FileTreeViewProvider fileTreeViewProvider,
        final PathWatcherProvider pathWatcherProvider) {
      this.fileTreeRepositoryProvider = fileTreeRepositoryProvider;
      this.fileTreeViewProvider = fileTreeViewProvider;
      this.pathWatcherProvider = pathWatcherProvider;
    }

    static Impl get(final Logger logger) {
      final PathWatcherProvider pathWatcherProvider = new PathWatcherProviderImpl(logger);
      final FileTreeRepositoryProvider fileTreeRepositoryProvider =
          new FileTreeRepositoryProviderImpl(pathWatcherProvider, logger);
      final FileTreeViewProvider fileTreeViewProvider = new FileTreeViewProviderImpl();
      return new Impl(fileTreeRepositoryProvider, fileTreeViewProvider, pathWatcherProvider);
    }

    @Override
    public FileTreeRepositoryProvider getFileTreeRepositoryProvider() {
      return fileTreeRepositoryProvider;
    }

    @Override
    public FileTreeViewProvider getFileTreeViewProvider() {
      return fileTreeViewProvider;
    }

    @Override
    public PathWatcherProvider getPathWatcherProvider() {
      return pathWatcherProvider;
    }
  }
}
