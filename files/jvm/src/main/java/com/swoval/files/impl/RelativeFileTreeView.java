package com.swoval.files.impl;

import com.swoval.files.api.FileTreeView;
import com.swoval.files.TypedPath;
import com.swoval.functional.Filter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RelativeFileTreeView implements FileTreeView<TypedPath> {
  private final Path path;
  private final FileTreeView<TypedPath> view;
  private final int maxDepth;
  private final Filter<? super TypedPath> filter;

  public RelativeFileTreeView(
      final Path path,
      final int maxDepth,
      final Filter<? super TypedPath> filter,
      final FileTreeView<TypedPath> view) {
    this.path = path;
    this.view = view;
    this.maxDepth = maxDepth;
    this.filter = filter;
  }

  @Override
  public List<TypedPath> list(final Path path, int maxDepth, final Filter<? super TypedPath> filter)
      throws IOException {
    if (!path.isAbsolute() || path.startsWith(this.path)) {
      final Path listPath = path.isAbsolute() ? path : this.path.resolve(path);
      final List<TypedPath> result = new ArrayList<>();
      final int baseDepth = path == this.path ? 0 : this.path.relativize(path).getNameCount();
      final int adjustedMaxDepth =
          maxDepth > Integer.MAX_VALUE - baseDepth ? Integer.MAX_VALUE : maxDepth + baseDepth;
      final int depth = adjustedMaxDepth < this.maxDepth ? adjustedMaxDepth : this.maxDepth;
      final Filter<? super TypedPath> combinedFilter =
          new Filter<TypedPath>() {
            @Override
            public boolean accept(final TypedPath typedPath) {
              return RelativeFileTreeView.this.filter.accept(typedPath) && filter.accept(typedPath);
            }
          };
      final Iterator<TypedPath> absoluteTypedPaths =
          view.list(listPath, depth - baseDepth, combinedFilter).iterator();
      while (absoluteTypedPaths.hasNext()) {
        final TypedPath absoluteTypedPath = absoluteTypedPaths.next();
        result.add(
            TypedPaths.getDelegate(
                path.relativize(absoluteTypedPath.getPath()), absoluteTypedPath));
      }
      return result;
    }
    return null;
  }

  @Override
  public void close() throws Exception {
    view.close();
  }
}
