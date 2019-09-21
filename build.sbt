import com.swoval.Build

val filesJS = Build.files.js
val filesJVM = Build.files.jvm
val nioJS = Build.nio.js
val plugin = Build.plugin
val swoval = Build.swoval
val scalagen = Build.scalagen
val testingJS = Build.testing.js
val testingJVM = Build.testing.jvm

Global / onChangedBuildSource := ReloadOnSourceChanges
