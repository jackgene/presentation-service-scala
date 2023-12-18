name := "presentation-service-ziohttp"

scalacOptions := Seq("-deprecation", "-Yexplicit-nulls", "-Ysafe-init")

val ZioConfigVersion = "3.0.7"

libraryDependencies += "dev.zio" %% "zio-cli" % "0.5.0"
libraryDependencies += "dev.zio" %% "zio-config" % ZioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-magnolia" % ZioConfigVersion
libraryDependencies += "dev.zio" %% "zio-config-typesafe" % ZioConfigVersion
libraryDependencies += "dev.zio" %% "zio-http" % "3.0.0-RC3"
libraryDependencies += "dev.zio" %% "zio-json" % "0.5.0"
libraryDependencies += "dev.zio" %% "zio-streams" % "2.0.19"
