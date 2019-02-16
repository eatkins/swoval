package com.swoval.files.impl;

import com.swoval.files.TypedPath;
import com.swoval.functional.Filter;

public class DirectoryRegistries {
  private DirectoryRegistries() {}

  public static DirectoryRegistry get() {
    return new DirectoryRegistryImpl();
  }

  static Filter<TypedPath> toTypedPathFilter(final DirectoryRegistry registry) {
    return new Filter<TypedPath>() {
      @Override
      public boolean accept(final TypedPath typedPath) {
        return registry.accept(typedPath.getPath());
      }
    };
  }
}
