import java.nio.file._
import java.util.concurrent.TimeUnit

import sjsonnew.BasicJsonProtocol._

import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.util.Properties

val jniInclude = taskKey[String]("the jni include directory")
"CC" := "g++"
"WIN64CC" := "x86_64-w64-mingw32-g++"
"INCLUDES" := m"-I$baseDirectory/src/include"
"CC_FLAGS" := m"-Wno-unused-command-line-argument -std=c++11 -O3 ${"INCLUDES"}"
"LIB_NAME" := "swoval-files0"

pat"$target/objects/apple/x86_64/%.o" :-
  (pat"src/main/apple/%.cc", pat"src/include/apple/%.hpp", pat"src/include/%.h") build
  sh(
    m"${"CC"} ${"CC_FLAGS"} $jniInclude ${"INCLUDES"}/apple -c ${`$<`} -framework Carbon -o ${`$@`}")
p"$target/x86_64/lib${"LIB_NAME"}.dylib" :- pat"$target/objects/apple/x86_64/%.o" build {
  sh(
    m"${"CC"} -dynamiclib -framework Carbon ${"CC_FLAGS"} -Wl,-headerpad_max_install_names" +
      m" -install_name @rpath/lib${"LIB_NAME"} ${`$^`} -o ${`$@`}")

}

pat"$target/objects/windows/x86_64/%.o" :-
  (pat"src/main/windows/%.cc", pat"src/include/%.h", pat"src/include/windows/%.h") build
  sh(m"${"WIN64CC"} ${"CC_FLAGS"} $jniInclude -c ${`$<`} -o ${`$@`} -D__WIN__")

p"$target/x86_64/${"LIB_NAME"}.dll" :- pat"$target/objects/windows/x86_64/%.o" build {
  sh(
    m"${"WIN64CC"} ${`$<`} ${"CC_FLAGS"} -Wl,-headerpad_max_install_names -o ${`$@`} " +
      "-D__WIN__ -Wall -Wextra  -nostdlib -ffreestanding -mconsole -Os -fno-stack-check " +
      "-fno-stack-protector -mno-stack-arg-probe -fno-leading-underscore -lkernel32 -fPIC -shared")
}

pat"$target/objects/linux/x86_64/%.o" :- pat"files/jni/src/main/posix/%.cc" build
  sh(m"${"CC"} ${"CC_FLAGS"} $jniInclude -c ${`$<`} -o ${`$@`}")
p"$target/x86_64/lib${"LIB_NAME"}.so" :- pat"$target/objects/linux/x86_64/" build
  sh(m"${"CC"} -shared ${`$<`} ${"CC_FLAGS"} -Wl,-wheaderpad_max_install_names -o ${`$@`}")

pat"$target/objects/freebsd/x86_64/%.o" :- pat"files/jni/src/main/posix/%.cc" build
  sh(m"${"CC"} ${"CC_FLAGS"} $jniInclude -c ${`$<`} -o ${`$@`}")
p"$target/x86_64/freebsd/lib${"LIB_NAME"}.so" :- pat"$target/objects/linux/x86_64/" build
  sh(m"${"CC"} -shared ${`$<`} ${"CC_FLAGS"} -Wl,-wheaderpad_max_install_names -o ${`$@`}")

TaskKey[Path]("buildMac") :- p"$target/x86_64/lib${"LIB_NAME"}.dylib" build { `$<` }
TaskKey[Path]("buildWindows") :- p"$target/x86_64/${"LIB_NAME"}.dll" build { `$<` }
TaskKey[Path]("buildLinux") :- p"$target/x86_64/lib${"LIB_NAME"}.so" build { `$<` }
TaskKey[Path]("buildFreeBSD") :- p"$target/x86_64/freebsd/lib${"LIB_NAME"}.so" build { `$<` }
TaskKey[Seq[Path]]("buildJNI") := Def
  .taskDyn[Seq[Path]] {
    if (Properties.isMac) {
      TaskKey[Path]("buildMac").zip(TaskKey[Path]("buildWindows")) {
        case (mac, win) => joinTasks(Seq(mac, win)).join
      }
    } else if (Properties.isLinux) {
      TaskKey[Path]("buildLinux").map(_ :: Nil)
    } else if (System.getProperty("os.name", "").startsWith("FreeBSD")) {
      TaskKey[Path]("buildFreeBSD").map(_ :: Nil)
    } else {
      throw new IllegalStateException("Unsupported platform " + System.getProperty("os.name"))
    }
  }
  .value

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
  val thread: Thread = new Thread() {
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
