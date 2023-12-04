name := "presentation-service-akkahttp"

scalacOptions ++= Seq("-new-syntax", "-deprecation", "-Yexplicit-nulls")

fork := true

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

val AkkaVersion = "2.9.0"
val AkkaHttpVersion = "10.6.0"

libraryDependencies += "com.github.pureconfig" %% "pureconfig-core" % "0.17.4"
libraryDependencies += "com.github.scopt" %% "scopt" % "4.1.0"
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion
libraryDependencies += "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5" excludeAll (
  "org.slf4j" % "slf4j-api"
)
libraryDependencies += "org.slf4j" % "slf4j-api" % "2.0.9"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.4.11" % Runtime excludeAll(
  "org.slf4j" % "slf4j-api"
)

libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.17" % "test"
