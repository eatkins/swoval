package com.swoval.files.impl;

import com.swoval.files.api.FileTreeView;
import com.swoval.files.cache.Entry;
import com.swoval.functional.Filter;
import java.nio.file.Path;
import java.util.List;

interface CachedDirectory<T> extends UpdatableFileTreeView<T>, AutoCloseable {

  /**
   * List the children of the path specified by {@link CachedDirectory#getPath()}, excluding the
   * {@link Entry entry} for the path itself. When the maxDepth parameter is <code>-1
   * </code>, return just the entry for the path itself.
   *
   * @param path the path to list
   * @param maxDepth the maximum depth of children
   * @param filter only include entries matching this filter
   * @return a list containing all of the entries included by the filter up to the max depth.
   */
  List<Entry<T>> list(final Path path, final int maxDepth, final Filter<? super Entry<T>> filter);

  /**
   * List all of the files for the {@code path}, returning only those files that are accepted by the
   * provided filter. Unlike {@link FileTreeView}, this implementation cannot throw an IOException
   * because list should be using the cache and not performing IO.
   *
   * @param maxDepth the maximum depth of subdirectories to query
   * @param filter include only paths accepted by the filter
   * @return a List of {@link java.nio.file.Path} instances accepted by the filter.
   */
  List<Entry<T>> list(final int maxDepth, final Filter<? super Entry<T>> filter);

  /**
   * Returns the {@link Entry} associated with the path specified by {@link
   * CachedDirectory#getPath()}.
   *
   * @return the entry
   */
  Entry<T> getEntry();

  int getMaxDepth();

  /** Catch any exceptions in close. */
  @Override
  void close();
}
