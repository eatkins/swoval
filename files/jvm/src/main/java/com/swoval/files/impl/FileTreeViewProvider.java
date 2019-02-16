package com.swoval.files.impl;

import com.swoval.files.FileTreeView;
import com.swoval.files.FileTreeViews;
import com.swoval.files.TypedPath;
import com.swoval.functional.Filter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class FileTreeViewProvider implements com.swoval.files.FileTreeViewProvider {
  private static final FileTreeViews.FollowSymlinks followSymlinks;
  private static final FileTreeViews.NoFollowSymlinks noFollowSymlinks;

  static {
    final DirectoryLister lister = DirectoryListers.INSTANCE;
    followSymlinks = new FollowWrapper(new SimpleFileTreeView(lister, true));
    noFollowSymlinks = new NoFollowWrapper(new SimpleFileTreeView(lister, false));
  }

  @Override
  public FileTreeViews.FollowSymlinks followSymlinks() {
    return followSymlinks;
  }

  @Override
  public FileTreeViews.NoFollowSymlinks noFollowSymlinks() {
    return noFollowSymlinks;
  }

  private static class Wrapper implements FileTreeView {
    @Override
    public List<TypedPath> list(
        final Path path, final int maxDepth, final Filter<? super TypedPath> filter)
        throws IOException {
      return delegate.list(path, maxDepth, filter);
    }

    @Override
    public void close() throws Exception {
      delegate.close();
    }

    private final FileTreeView delegate;

    Wrapper(final FileTreeView delegate) {
      this.delegate = delegate;
    }
  }

  private static class FollowWrapper extends Wrapper implements FileTreeViews.FollowSymlinks {
    FollowWrapper(final FileTreeView delegate) {
      super(delegate);
    }
  }

  private static class NoFollowWrapper extends Wrapper implements FileTreeViews.NoFollowSymlinks {
    NoFollowWrapper(final FileTreeView delegate) {
      super(delegate);
    }
  }
}
