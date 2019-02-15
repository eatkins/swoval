package com.swoval.files
import com.swoval.logging.Loggers.Level
import com.swoval.logging.{ Logger, Loggers }

import scala.util.control.NonFatal

/**
 * Provides an execution context to run tasks. Exists to allow source interoperability with the jvm
 * interoperability.
 */
private[files] abstract class Executor extends AutoCloseable {
  private[this] var _closed = false

  def run(runnable: Runnable, priority: Int): Unit = {
    try {
      runnable.run()
    } catch {
      case e: Exception =>
        System.err.println(s"Error running: $runnable\n$e\n${e.getStackTrace mkString "\n"}")
    }
  }
  def run(runnable: Runnable): Unit = run(runnable, -1)

  /**
   * Is this executor available to invoke callbacks?
   *
   * @return true if the executor is not closed
   */
  def isClosed(): Boolean = _closed

  override def close(): Unit = _closed = true
}

object Executor {

  /**
   * Make a new instance of an Executor
   *
   * @param name Unused but exists for jvm source compatibility
   * @return
   */
  def make(name: String, logger: Logger): Executor = new Executor {
    override def run(runnable: Runnable): Unit =
      try runnable.run()
      catch {
        case NonFatal(e) =>
          if (Loggers.shouldLog(logger, Level.ERROR))
            Loggers.logException(logger, e)
      }
  }
}
