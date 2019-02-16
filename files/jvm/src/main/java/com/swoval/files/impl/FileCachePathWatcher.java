package com.swoval.files.impl;

import static com.swoval.functional.Filters.AllPass;

import com.swoval.files.FileTreeDataViews.Entry;
import com.swoval.files.api.PathWatcher;
import com.swoval.files.PathWatchers.Event;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;

class FileCachePathWatcher<T> implements AutoCloseable {
  private final SymlinkWatcher symlinkWatcher;
  private final PathWatcher<Event> pathWatcher;
  private final FileCacheDirectoryTree<T> tree;

  FileCachePathWatcher(final FileCacheDirectoryTree<T> tree, final PathWatcher<Event> pathWatcher) {
    this.symlinkWatcher = tree.symlinkWatcher;
    this.pathWatcher = pathWatcher;
    this.tree = tree;
  }

  boolean register(final Path path, final int maxDepth) throws IOException {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    final CachedDirectory<T> dir = tree.register(absolutePath, maxDepth, pathWatcher);
    if (dir != null && symlinkWatcher != null) {
      if (dir.getEntry().getTypedPath().isSymbolicLink()) {
        symlinkWatcher.addSymlink(absolutePath, maxDepth);
      }
      final Iterator<Entry<T>> it = dir.listEntries(dir.getMaxDepth(), AllPass).iterator();
      while (it.hasNext()) {
        final Entry<T> entry = it.next();
        if (entry.getTypedPath().isSymbolicLink()) {
          final int depth = absolutePath.relativize(entry.getTypedPath().getPath()).getNameCount();
          symlinkWatcher.addSymlink(
              entry.getTypedPath().getPath(),
              maxDepth == Integer.MAX_VALUE ? maxDepth : maxDepth - depth);
        }
      }
    }
    return dir != null;
  }

  void unregister(final Path path) {
    final Path absolutePath = path.isAbsolute() ? path : path.toAbsolutePath();
    tree.unregister(absolutePath);
    pathWatcher.unregister(absolutePath);
  }

  public void close() {
    pathWatcher.close();
    if (symlinkWatcher != null) symlinkWatcher.close();
  }
}
