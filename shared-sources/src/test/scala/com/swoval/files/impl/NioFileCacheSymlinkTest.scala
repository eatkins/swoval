package com.swoval.files
package impl

import com.swoval.runtime.Platform
import utest._

object NioFileCacheSymlinkTest extends FileCacheSymlinkTest with NioFileCacheTest {
  override val tests: Tests =
    if (Platform.isJVM && Platform.isMac) testsImpl
    else
      Tests('ignore - {
        if (com.swoval.test.verbose)
          println("Not running NioFileCacheTest on platform other than the jvm on osx")
      })
}
