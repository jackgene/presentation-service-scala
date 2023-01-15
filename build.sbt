lazy val commonSettings = Seq(
  organization := "com.jackleow",
  version := "1.0",
)

lazy val `service-play` = (project in file("service-play")).
  settings(commonSettings).
  enablePlugins(PlayScala)
lazy val benchmark = (project in file("benchmark")).
  settings(commonSettings).
  dependsOn(`service-play`)
