package com.swoval.files;

import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.files.FileTreeRepositories.FollowSymlinks;
import com.swoval.files.FileTreeRepositories.NoFollowSymlinks;
import com.swoval.logging.Logger;
import java.io.IOException;

class SwovalProviderImpl implements SwovalProvider {
  private static final SwovalProvider impl = load();

  static SwovalProvider getDefaultProvider() {
    return impl;
  }

  private static SwovalProvider load() {
    final SwovalProvider userDefined = SwovalProviderFactory.loadProvider();
    return userDefined != null ? userDefined : new Impl();
  }

  private static class Impl implements SwovalProvider {
    final FileTreeRepositoryProvider fileTreeRepositoryProvider =
        new FileTreeRepositoryProviderImpl();

    @Override
    public FileTreeRepository<Object> getDefault() throws InterruptedException, IOException {
      return fileTreeRepositoryProvider.getDefault();
    }

    @Override
    public <T> FollowSymlinks<T> followSymlinks(Converter<T> converter, Logger logger)
        throws InterruptedException, IOException {
      return fileTreeRepositoryProvider.followSymlinks(converter, logger);
    }

    @Override
    public <T> NoFollowSymlinks<T> noFollowSymlinks(Converter<T> converter, Logger logger)
        throws InterruptedException, IOException {
      return fileTreeRepositoryProvider.noFollowSymlinks(converter, logger);
    }
  }

  @Override
  public FileTreeRepository<Object> getDefault() throws InterruptedException, IOException {
    return SwovalProviderImpl.impl.getDefault();
  }

  @Override
  public <T> FollowSymlinks<T> followSymlinks(Converter<T> converter, Logger logger)
      throws InterruptedException, IOException {
    return SwovalProviderImpl.impl.followSymlinks(converter, logger);
  }

  @Override
  public <T> NoFollowSymlinks<T> noFollowSymlinks(Converter<T> converter, Logger logger)
      throws InterruptedException, IOException {
    return SwovalProviderImpl.impl.noFollowSymlinks(converter, logger);
  }
}
