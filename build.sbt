lazy val commonSettings = Seq(
  organization := "com.jackleow",
  version := "1.0",
  scalaVersion := "3.3.1"
)

lazy val shared = (project in file("shared")).
  settings(commonSettings)
lazy val `service-play` = (project in file("service-play")).
  settings(commonSettings).
  enablePlugins(PlayScala).
  dependsOn(shared)
lazy val benchmark = (project in file("benchmark")).
  settings(commonSettings).
  dependsOn(shared)
