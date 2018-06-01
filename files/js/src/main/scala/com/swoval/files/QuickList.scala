package com.swoval.files

import java.nio.file.Path
import java.util.List

object QuickList {
  private val INSTANCE: QuickLister =
    if (Platform.isJVM) new NativeQuickLister() else new NioQuickLister()

  def list(path: Path, maxDepth: Int): List[QuickFile] =
    INSTANCE.list(path, maxDepth, followLinks = true)

  def list(path: Path, maxDepth: Int, followLinks: Boolean): List[QuickFile] =
    INSTANCE.list(path, maxDepth, followLinks)

}