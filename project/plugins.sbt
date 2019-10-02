val bundlerVersion = Option(System.getProperty("bundler.version")).getOrElse("0.14.0")
val crossprojectVersion = "0.4.0"
val scalaJSVersion = Option(System.getProperty("scala.js.version")).getOrElse("0.6.29")

addSbtPlugin("com.swoval" % "sbt-source-format" % "0.1.6")

addSbtPlugin("com.github.sbt" % "sbt-jacoco" % "3.1.0")

addSbtPlugin("com.swoval" % "sbt-make" % "0.1.1")

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion)

addSbtPlugin("org.portable-scala" % "sbt-crossproject" % crossprojectVersion)

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % crossprojectVersion)

addSbtPlugin("ch.epfl.scala" % "sbt-scalajs-bundler" % bundlerVersion)

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "2.0")

addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.1.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.0.6")


