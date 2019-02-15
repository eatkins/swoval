package com.swoval.files.impl;

import com.swoval.files.impl.SimpleFileTreeView.ListResults;
import java.io.IOException;

public interface DirectoryLister {
  ListResults apply(final String dir, final boolean followLinks) throws IOException;
}
