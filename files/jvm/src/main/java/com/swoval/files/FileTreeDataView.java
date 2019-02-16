package com.swoval.files;

import com.swoval.files.FileTreeDataViews.Entry;
import com.swoval.files.api.FileTreeView;
import com.swoval.functional.Filter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * A repository for which each {@link java.nio.file.Path} has an associated data value.
 *
 * @param <T> the data value for each path
 */
public interface FileTreeDataView<T> extends FileTreeView<Entry<T>>, AutoCloseable {
  /**
   * List all of the files for the {@code path</code> that are accepted by the <code>filter}.
   *
   * @param path the path to list. If this is a file, returns a list containing the Entry for the
   *     file or an empty list if the file is not monitored by the path.
   * @param maxDepth the maximum depth of subdirectories to return
   * @param filter include only paths accepted by this
   * @return a List of {@link Entry} instances accepted by the filter. The list will be empty if the
   *     path is not a subdirectory of this CachedDirectory or if it is a subdirectory, but the
   *     CachedDirectory was created without the recursive flag.
   * @throws IOException if the path cannot be listed.
   */
  List<Entry<T>> list(final Path path, final int maxDepth, final Filter<? super Entry<T>> filter)
      throws IOException;
}
