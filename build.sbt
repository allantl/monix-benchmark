name := "monix-benchmark"

version := "0.1"

scalaVersion := "2.12.8"

libraryDependencies ++= Seq(
  "io.monix" %% "monix" % "3.0.0-RC3-925c563-SNAPSHOT",
  "org.typelevel" %% "cats-effect" % "1.4.0"
)

enablePlugins(JmhPlugin)

fork in run := true