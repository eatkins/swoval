package com.swoval.files.impl
import java.nio.file.Path

import com.swoval.files.TypedPath

object TestTypedPaths {
  def get(path: Path): TypedPath = TypedPaths.get(path)
  def get(path: Path, kind: Int): TypedPath = TypedPaths.get(path, kind)
}
