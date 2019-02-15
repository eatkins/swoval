package com.swoval.files.impl;

import com.swoval.files.FileTreeDataViews.Converter;
import com.swoval.files.TypedPath;

public class Converters {
  private static final Object UNIT_VALUE = new Object();
  static final Converter<Object> UNIT_CONVERTER =
      new Converter<Object>() {
        @Override
        public Object apply(final TypedPath typedPath) {
          return UNIT_VALUE;
        }
      };
}
