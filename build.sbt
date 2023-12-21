scalaVersion := "3.3.3"

lazy val commonSettings = Seq(
  organization := "com.jackleow",
  version := "1.0",
  scalaVersion := "3.3.3",
  scalacOptions ++= Seq(
    "-feature", "-explain",
    "-Wunused:imports,privates,locals,explicits,implicits,params,linted",
    "-Wvalue-discard",
    "-Xfatal-warnings"
  ),
)

lazy val shared = (project in file("shared")).
  settings(commonSettings)
lazy val `service-akkahttp` = (project in file("service-akkahttp")).
  settings(commonSettings).
  dependsOn(shared)
lazy val `service-play` = (project in file("service-play")).
  settings(commonSettings).
  enablePlugins(PlayScala).
  dependsOn(shared)
lazy val `service-kyotapir` = (project in file("service-kyotapir")).
  settings(commonSettings).
  dependsOn(shared)
lazy val `service-ziohttp` = (project in file("service-ziohttp")).
  settings(commonSettings).
  dependsOn(shared)
lazy val benchmark = (project in file("benchmark")).
  settings(commonSettings).
  dependsOn(shared)
