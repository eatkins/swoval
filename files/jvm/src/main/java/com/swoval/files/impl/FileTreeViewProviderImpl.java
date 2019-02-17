package com.swoval.files.impl;

import com.swoval.files.TypedPath;
import com.swoval.files.api.FileTreeView;

class FileTreeViewProviderImpl implements com.swoval.files.FileTreeViewProvider {
  private static final FileTreeView<TypedPath> followSymlinksView;
  private static final FileTreeView<TypedPath> noFollowSymlinksView;

  static {
    final DirectoryLister lister = DirectoryListers.INSTANCE;
    followSymlinksView = new SimpleFileTreeView(lister, true);
    noFollowSymlinksView = new SimpleFileTreeView(lister, false);
  }

  @Override
  public FileTreeView<TypedPath> get(boolean followSymlinks) {
    return followSymlinks ? followSymlinksView : noFollowSymlinksView;
  }
}
