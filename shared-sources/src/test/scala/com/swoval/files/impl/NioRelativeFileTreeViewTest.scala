package com.swoval.files.impl
import java.nio.file.Path
import java.util

import com.swoval.files.api.FileTreeView
import com.swoval.files.{ FileTreeViews, RelativeFileTreeViewTest, TypedPath }
import com.swoval.functional.Filter
import com.swoval.functional.Filters.AllPass

import scala.collection.JavaConverters._

object NioRelativeFileTreeViewTest
    extends RelativeFileTreeViewTest(
      (path: Path, depth: Int, followLinks: Boolean) =>
        new RelativeFileTreeViewImpl(path,
                                     depth,
                                     AllPass,
                                     new SimpleFileTreeView(new NioDirectoryLister, followLinks)))
object DirectoryRelativeFileTreeViewTest
    extends RelativeFileTreeViewTest((path: Path, depth: Int, followLinks: Boolean) => {
      val backing: CachedDirectoryImpl[TypedPath] =
        new CachedDirectoryImpl(TypedPaths.get(path),
                                identity[TypedPath],
                                depth,
                                AllPass,
                                followLinks,
                                if (followLinks) FileTreeViews.followSymlinks
                                else FileTreeViews.noFollowSymlinks).init()
      val view = new FileTreeView[TypedPath] {
        override def list(path: Path,
                          maxDepth: Int,
                          filter: Filter[_ >: TypedPath]): util.List[TypedPath] =
          backing
            .list(path, maxDepth, AllPass)
            .asScala
            .flatMap(e => if (filter.accept(e.getTypedPath)) Some(e.getTypedPath) else None)
            .asJava
        override def close(): Unit = {}
      }
      new RelativeFileTreeViewImpl(path, depth, AllPass, view)
    })
