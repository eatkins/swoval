package com.swoval.files
package impl

import com.swoval.runtime.Platform
import utest._

object NioBasicFileCacheTest extends BasicFileCacheTest with NioFileCacheTest {
  val tests =
    if (Platform.isJVM && Platform.isMac) testsImpl
    else
      Tests('ignore - {
        if (com.swoval.test.verbose)
          println("Not running NioFileCacheTest on platform other than the jvm on osx")
      })
}
