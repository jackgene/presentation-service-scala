name := "presentation-common"

scalacOptions ++= Seq("-deprecation", "-no-indent")

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % Test
libraryDependencies += "org.scalatestplus" %% "scalacheck-1-17" % "3.2.17.0" % Test

Test / fork := false
