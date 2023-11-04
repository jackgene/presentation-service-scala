lazy val commonSettings = Seq(
  organization := "com.jackleow",
  version := "1.0",
  scalaVersion := "3.3.1"
)

lazy val `service-play` = (project in file("service-play")).
  settings(commonSettings).
  enablePlugins(PlayScala)
lazy val benchmark = (project in file("benchmark")).
  settings(
    commonSettings,
    excludeDependencies ++= Seq(
      // Play Framework 2.9.0, despite being Scala 3, pulls in Scala 2.13 versions of these
      // And we aren't benchmarking anything that depends on these anyway
      "com.fasterxml.jackson.module",
      "com.typesafe",
      "com.typesafe.akka"
    )
  ).
  dependsOn(`service-play`)
