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

if (Properties.isMac) {
  val mac = p"${filesJVM / Compile / resourceDirectory}/native/x86_64/lib${"LIB_NAME"}.dylib" :-
    p"${jni / target}/x86_64/lib${"LIB_NAME"}.dylib" build Files.copy(`$<`, `$@`, REPLACE_EXISTING)
  val win = p"${filesJVM / Compile / resourceDirectory}/native/x86_64/${"LIB_NAME"}.dll" :-
    p"${jni / target}/x86_64/${"LIB_NAME"}.dll" build Files.copy(`$<`, `$@`, REPLACE_EXISTING)
  mac ++ win ++ (TaskKey[Unit]("buildJNI") :-
    (p"${filesJVM / Compile / resourceDirectory}/native/x86_64/lib${"LIB_NAME"}.dylib",
    p"${filesJVM / Compile / resourceDirectory}/native/x86_64/${"LIB_NAME"}.dll") build { () })
} else Nil

filesJVM / Compile / compile := {
  TaskKey[Unit]("buildJNI").value
  (filesJVM / Compile / compile).value
}

Global / onChangedBuildSource := ReloadOnSourceChanges
