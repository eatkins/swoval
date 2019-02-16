package com.swoval.files;

import com.swoval.files.api.FileTreeView;
import com.swoval.files.impl.SwovalProviderImpl;
import com.swoval.functional.Filter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Provides static methods returning instances of the various view interfaces defined throughout
 * this package.
 */
public class FileTreeViews {

  private FileTreeViews() {}

  private static final FileTreeViewProvider fileTreeViewProvider =
      SwovalProviderImpl.getDefaultProvider().getFileTreeViewProvider();

  public static FollowSymlinks followSymlinks() {
    return new FollowWrapper(fileTreeViewProvider.get(true));
  }

  public static NoFollowSymlinks noFollowSymlinks() {
    return new NoFollowWrapper(fileTreeViewProvider.get(false));
  }

  //  public static FollowSymlinks relativeFollowSymlinks() {}
  //  public static FollowSymlinks relativeNoFollowSymlinks() {}

  public interface FollowSymlinks extends FileTreeView<TypedPath> {};

  public interface NoFollowSymlinks extends FileTreeView<TypedPath> {};

  private static class Wrapper implements FileTreeView<TypedPath> {
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

    private final FileTreeView<TypedPath> delegate;

    Wrapper(final FileTreeView<TypedPath> delegate) {
      this.delegate = delegate;
    }
  }

  private static class FollowWrapper extends Wrapper implements FileTreeViews.FollowSymlinks {
    FollowWrapper(final FileTreeView<TypedPath> delegate) {
      super(delegate);
    }
  }

  private static class NoFollowWrapper extends Wrapper implements FileTreeViews.NoFollowSymlinks {
    NoFollowWrapper(final FileTreeView<TypedPath> delegate) {
      super(delegate);
    }
  }
}
