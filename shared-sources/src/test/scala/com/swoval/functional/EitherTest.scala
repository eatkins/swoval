package com.swoval.functional

import com.swoval.files.impl.functional.EitherImpl
import com.swoval.functional.throwables.{ NotLeftException, NotRightException }
import utest._

object EitherTest extends TestSuite {
  override def tests: Tests = Tests {
    'exceptions - {
      'left - {
        intercept[NotLeftException](EitherImpl.right(1).leftValue())
        ()
      }
      'right - {
        intercept[NotRightException](EitherImpl.left(1).rightValue())
        ()
      }
    }
    'type - {
      'left - {
        val left = EitherImpl.left(1)
        assert(!left.isRight)
      }
      'right - {
        val right = EitherImpl.right(1)
        assert(right.isRight)
      }
    }
  }
}
