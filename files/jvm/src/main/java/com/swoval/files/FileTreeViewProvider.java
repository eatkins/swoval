package com.swoval.files;

public interface FileTreeViewProvider {
  FileTreeViews.FollowSymlinks followSymlinks();

  FileTreeViews.NoFollowSymlinks noFollowSymlinks();
}
