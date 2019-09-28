import com.swoval.Build

val filesJS = Build.files.js
val filesJVM = Build.files.jvm
val nioJS = Build.nio.js
val plugin = Build.plugin
val swoval = Build.swoval
val scalagen = Build.scalagen
val testingJS = Build.testing.js
val testingJVM = Build.testing.jvm
val jni = Build.jni

filesJVM / Compile / compile := {
  (jni / Compile / compile).value
  (filesJVM / Compile / compile).value
}

Global / onChangedBuildSource := ReloadOnSourceChanges
