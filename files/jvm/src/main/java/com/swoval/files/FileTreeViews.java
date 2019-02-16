package com.swoval.files;

import com.swoval.files.impl.SwovalProviderImpl;

/**
 * Provides static methods returning instances of the various view interfaces defined throughout
 * this package.
 */
public class FileTreeViews {

  private FileTreeViews() {}

  private static final FileTreeViewProvider fileTreeViewProvider =
      SwovalProviderImpl.getDefaultProvider().getFileTreeViewProvider();

  public static FollowSymlinks followSymlinks() {
    return fileTreeViewProvider.followSymlinks();
  }

  public static NoFollowSymlinks noFollowSymlinks() {
    return fileTreeViewProvider.noFollowSymlinks();
  }

  public interface FollowSymlinks extends FileTreeView {};

  public interface NoFollowSymlinks extends FileTreeView {};
}
