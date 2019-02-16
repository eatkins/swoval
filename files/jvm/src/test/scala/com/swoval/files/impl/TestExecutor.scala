package com.swoval.files.impl
import com.swoval.files.test.TestLogger

trait TestExecutor extends AutoCloseable {
  def run[R](f: () => R): Unit
}
object TestExecutor {
  def make(threadName: String)(implicit testLogger: TestLogger): TestExecutor =
    new TestExecutor {
      val executor = Executor.make(threadName, testLogger)
      override def run[R](f: () => R): Unit =
        executor.run(new Runnable { override def run(): Unit = f() })
      override def close(): Unit = executor.close()

    }
}
