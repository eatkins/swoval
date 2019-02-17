package com.swoval.files;

import com.swoval.files.api.FileTreeView;
import com.swoval.functional.Filter;
import java.io.IOException;
import java.util.List;

public interface RelativeFileTreeView<T> extends FileTreeView<T> {
  List<T> list(int maxDepth, Filter<? super T> filter) throws IOException;
}
