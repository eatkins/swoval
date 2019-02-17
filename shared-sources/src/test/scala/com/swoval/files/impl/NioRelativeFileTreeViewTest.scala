package com.swoval.files.impl
import java.nio.file.Path

import com.swoval.files.RelativeFileTreeViewTest
import com.swoval.functional.Filters.AllPass

object NioRelativeFileTreeViewTest
  extends RelativeFileTreeViewTest(
    (path: Path, depth: Int, followLinks: Boolean) =>
      new RelativeFileTreeViewImpl(path,
        depth,
        AllPass,
        new SimpleFileTreeView(new NioDirectoryLister, followLinks)))
