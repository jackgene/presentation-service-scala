name := "presentation-service-kyotapir"

scalacOptions := Seq("-deprecation", "-Yexplicit-nulls", "-Ysafe-init")

val KyoVersion = "0.8.0"

libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0"
libraryDependencies += "io.getkyo" %% "kyo-core" % KyoVersion
libraryDependencies += "io.getkyo" %% "kyo-direct" % KyoVersion
libraryDependencies += "io.getkyo" %% "kyo-tapir" % KyoVersion
