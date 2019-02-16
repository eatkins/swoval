package com.swoval.files;

import com.swoval.files.api.FileTreeView;

public interface FileTreeViewProvider {
  FileTreeView<TypedPath> get(boolean followSymlinks);
}
