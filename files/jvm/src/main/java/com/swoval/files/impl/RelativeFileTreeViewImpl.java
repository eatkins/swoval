package com.swoval.files.impl;

import static com.swoval.functional.Filters.AllPass;

import com.swoval.files.RelativeFileTreeView;
import com.swoval.files.TypedPath;
import com.swoval.files.api.FileTreeView;
import com.swoval.functional.Filter;
import java.io.IOException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RelativeFileTreeViewImpl implements RelativeFileTreeView<TypedPath> {
  private final Path path;
  private final FileTreeView<TypedPath> view;
  private final int maxDepth;
  private final Filter<? super TypedPath> baseFilter;

  public RelativeFileTreeViewImpl(
      final Path path,
      final int maxDepth,
      final Filter<? super TypedPath> filter,
      final FileTreeView<TypedPath> view) {
    this.path = path;
    this.view = view;
    this.maxDepth = maxDepth;
    this.baseFilter = filter;
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
              return RelativeFileTreeViewImpl.this.baseFilter.accept(typedPath)
                  && filter.accept(typedPath);
            }
          };
      try {
        final Iterator<TypedPath> absoluteTypedPaths =
            view.list(listPath, depth - baseDepth, AllPass).iterator();
        while (absoluteTypedPaths.hasNext()) {
          final TypedPath absoluteTypedPath = absoluteTypedPaths.next();
          if (filter.accept(absoluteTypedPath)) {
            result.add(
                TypedPaths.getDelegate(
                    this.path.relativize(absoluteTypedPath.getPath()), absoluteTypedPath));
          } else {
          }
        }
      } catch (final NotDirectoryException e) {
        if (maxDepth != -1) throw e;
        result.add(TypedPaths.getDelegate(path, TypedPaths.get(this.path.resolve(path))));
      }
      return result;
    } else {
      throw new IllegalArgumentException(
          path + " is neither relative nor starts with the base path " + path);
    }
  }

  @Override
  public void close() throws Exception {
    view.close();
  }

  @Override
  public List<TypedPath> list(int maxDepth, Filter<? super TypedPath> filter) throws IOException {
    return list(path, maxDepth, filter);
  }
}
