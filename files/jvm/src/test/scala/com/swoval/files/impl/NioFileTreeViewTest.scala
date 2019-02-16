package com.swoval.files.impl
import java.nio.file.Path

import com.swoval.files.{ FileTreeViewTest, TypedPath }
import com.swoval.functional.Filters.AllPass

object NioFileTreeViewTest
    extends FileTreeViewTest(
      (path: Path, depth: Int, followLinks: Boolean) =>
        new CachedDirectoryImpl[Path](
          TypedPaths.get(path),
          (tp: TypedPath) => tp.getPath,
          depth,
          AllPass,
          followLinks,
          new SimpleFileTreeView(new NioDirectoryLister, followLinks)).init())
