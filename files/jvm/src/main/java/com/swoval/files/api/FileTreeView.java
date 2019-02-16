package com.swoval.files.api;

import com.swoval.functional.Filter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FileTreeView<T> extends AutoCloseable {
  /**
   * List all of the files for the {@code path}, returning only those files that are accepted by the
   * provided filter.
   *
   * @param path the root path to list
   * @param maxDepth the maximum depth of subdirectories to query
   * @param filter include only paths accepted by the filter
   * @return a List of {@link java.nio.file.Path} instances accepted by the filter.
   * @throws IOException if the path cannot be listed.
   */
  List<T> list(final Path path, final int maxDepth, final Filter<? super T> filter)
      throws IOException;
}
