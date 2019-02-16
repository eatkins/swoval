package com.swoval.files.impl;

import com.swoval.files.api.FileTreeView;
import com.swoval.files.TypedPath;
import com.swoval.functional.Filter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

class RelativeFileTreeView implements FileTreeView {
  private final Path path;
  private final FileTreeView view;

  RelativeFileTreeView(final Path path, final FileTreeView view) {
    this.path = path;
    this.view = view;
  }

  @Override
  public List<TypedPath> list(final Path path, int maxDepth, Filter<? super TypedPath> filter)
      throws IOException {
    if (!path.isAbsolute() || path.startsWith(this.path)) {
      final Path listPath = path.isAbsolute() ? path : this.path.resolve(path);
      final List<TypedPath> result = new ArrayList<>();
      final Iterator<TypedPath> absoluteTypedPaths =
          view.list(listPath, maxDepth, filter).iterator();
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
