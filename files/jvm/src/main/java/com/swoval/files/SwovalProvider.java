package com.swoval.files;

public interface SwovalProvider {
  FileTreeRepositoryProvider getFileTreeRepositoryProvider();

  PathWatcherProvider getPathWatcherProvider();
}
