package com.swoval.files;

public interface SwovalProvider {
  FileTreeRepositoryProvider getFileTreeRepositoryProvider();

  FileTreeViewProvider getFileTreeViewProvider();

  PathWatcherProvider getPathWatcherProvider();
}
