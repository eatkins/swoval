import java.nio.file._
import java.util.concurrent.TimeUnit
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Properties
import sjsonnew.BasicJsonProtocol._

val jniInclude = taskKey[String]("the jni include directory")
def getProcOutput(args: String*): String = {
  val proc = new ProcessBuilder(args: _*).start()
  val out = new java.util.Vector[Byte]
  val err = new java.util.Vector[Byte]
  val is = proc.getInputStream
  val es = proc.getErrorStream
  def drain(): Unit = {
    while (is.available > 0) out.add((is.read() & 0xFF).toByte)
    while (es.available > 0) err.add((es.read() & 0xFF).toByte)
  }
  val thread = new Thread() {
    setDaemon(true)
    start()
    val stopped = new java.util.concurrent.atomic.AtomicBoolean(false)
    @tailrec
    override def run(): Unit = {
      drain()
      if (proc.isAlive && !stopped.get && !Thread.currentThread.isInterrupted) {
        try Thread.sleep(10)
        catch { case _: InterruptedException => stopped.set(true) }
        run()
      }
    }
  }
  proc.waitFor(5, TimeUnit.SECONDS)
  thread.interrupt()
  thread.join(5000)
  drain()
  if (!err.isEmpty) System.err.println(new String(err.asScala.toArray))
  new String(out.asScala.toArray)
}
def parentPath(args: String*)(cond: String => Boolean): Option[Path] =
  getProcOutput(args: _*).linesIterator.collectFirst {
    case l if cond(l) =>
      val f = Paths.get(l)
      assert(Files.exists(f), s"$f did not exist")
      f.getParent
  }
val cc = taskKey[String]("compiler")
val ccFlags = taskKey[String]("compiler flags")
Global / jniInclude := {
  (Global / jniInclude).previous.getOrElse {
    System.getProperty("java8.home") match {
      case null =>
        if (Properties.isMac) {
          parentPath("mdfind", "-name", "jni.h")(_.contains("jdk1.8"))
            .map(p => s"-I$p -I$p/darwin")
            .getOrElse {
              throw new IllegalStateException("Couldn't find jni.h for jdk 8")
            }
        } else {
          parentPath("locate", "jni.h")(_ => true).map(p => s"-I$p -I$p/linux").getOrElse {
            throw new IllegalStateException("Couldn't find jni.h for jdk 8")
          }
        }
      case h =>
        val platform = if (Properties.isMac) "darwin" else "linux"
        s"-I$h/include/ -i$h/include/$platform"
    }
  }
}
"CC" := { if (Properties.isMac) "clang" else "gcc" }
"WIN64CC" := "x86_64-w64-mingw32-g++"
"INCLUDES" := m"-I$baseDirectory/src/include"
"CC_FLAGS" := m"-Wno-unused-command-line-argument -std=c++11 -O3 ${"INCLUDES"}"
pat"$target/objects/apple/x86_64/%.o" :- pat"files/jni/src/main/apple/%.cc" build
  sh(
    m"${"CC"} ${"CC_FLAGS"} $jniInclude ${"INCLUDES"}/apple -c ${`$^`} -framework Carbon -o ${`$@`}")
pat"$target/objects/windows/x86_64/%.o" :- pat"files/jni/src/main/windows/%.cc" build
  sh(m"${"WIN64CC"} ${"CC_FLAGS"} $jniInclude -c ${`$^`} -o ${`$@`} -D__WIN__")
pat"$target/objects/linux/x86_64/%.o" :- pat"files/jni/src/main/linux/%.cc" build
  sh(m"${"WIN64CC"} ${"CC_FLAGS"} $jniInclude -c ${`$^`} -o ${`$@`} -D__WIN__")
TaskKey[Unit]("buildJNI") := Def.taskDyn {
  if (Properties.isMac) {
    val x = Def
      .task(pat"$target/objects/apple/x86_64/%.o".make)
      .zip(Def.task(pat"$target/objects/windows/x86_64/%.o".make))
      .flatMap {
        case (x, y) =>
          val z = Seq(x, y).join.map(_ => ())
          z
      }
    x

  } else {
    Def.task[Unit](???)
  }
}.value
