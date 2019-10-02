import com.swoval.Build

import scala.util.Properties
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

val filesJS = Build.files.js
val filesJVM = Build.files.jvm
val nioJS = Build.nio.js
val plugin = Build.plugin
val swoval = Build.swoval
val scalagen = Build.scalagen
val testingJS = Build.testing.js
val testingJVM = Build.testing.jvm
val jni = Build.jni

val buildJNIImpl = taskKey[Unit]("build jni").withRank(Int.MaxValue)
val buildJNI = taskKey[Unit]("build jni")

if (Properties.isMac) {
  val mac = p"${filesJVM / Compile / resourceDirectory}/native/x86_64/lib${"LIB_NAME"}.dylib" :-
    p"${jni / target}/x86_64/lib${"LIB_NAME"}.dylib" build Files.copy(`$<`, `$@`, REPLACE_EXISTING)
  val win = p"${filesJVM / Compile / resourceDirectory}/native/x86_64/${"LIB_NAME"}.dll" :-
    p"${jni / target}/x86_64/${"LIB_NAME"}.dll" build Files.copy(`$<`, `$@`, REPLACE_EXISTING)
  mac ++ win ++ (buildJNIImpl :-
    (p"${filesJVM / Compile / resourceDirectory}/native/x86_64/lib${"LIB_NAME"}.dylib",
    p"${filesJVM / Compile / resourceDirectory}/native/x86_64/${"LIB_NAME"}.dll") build { () })
} else if (Properties.isLinux) {
  (p"${filesJVM / Compile / resourceDirectory}/native/x86_64/lib${"LIB_NAME"}.so" :-
    p"${jni / target}/x86_64/lib${"LIB_NAME"}.so" build Files.copy(`$<`, `$@`, REPLACE_EXISTING)) ++
    (buildJNIImpl :-
      p"${filesJVM / Compile / resourceDirectory}/native/x86_64/lib${"LIB_NAME"}.so" build { () })
} else if (System.getProperty("os.name").startsWith("FreeBSD")) {
  (p"${filesJVM / Compile / resourceDirectory}/native/x86_64/freebsd/lib${"LIB_NAME"}.so" :-
    p"${jni / target}/x86_64/lib${"LIB_NAME"}.so" build Files.copy(`$<`, `$@`, REPLACE_EXISTING)) ++
    (buildJNIImpl :-
      p"${filesJVM / Compile / resourceDirectory}/native/x86_64/freebsd/lib${"LIB_NAME"}.so" build {
      ()
    })
} else Nil

buildJNI := buildJNIImpl.value
buildJNI := buildJNI.dependsOn(filesJVM / Compile / compile).value

filesJVM / Compile / fullClasspath := {
  buildJNI.value
  (filesJVM / Compile / fullClasspath).value
}

filesJVM / Test / fullClasspath := {
  buildJNI.value
  (filesJVM / Test / fullClasspath).value
}

Global / onChangedBuildSource := ReloadOnSourceChanges
