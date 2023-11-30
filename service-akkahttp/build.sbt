name := "presentation-service-akkahttp"

scalacOptions := Seq("-new-syntax", "-deprecation")

fork := true

resolvers += "Akka library repository".at("https://repo.akka.io/maven")

val AkkaVersion = "2.9.0"
val AkkaHttpVersion = "10.6.0"
libraryDependencies ++= Seq(
  "com.github.pureconfig" %% "pureconfig-core" % "0.17.4",
  "com.github.scopt" %% "scopt" % "4.1.0",
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5" excludeAll (
    "org.slf4j" % "slf4j-api"
  ),
  "org.slf4j" % "slf4j-api" % "2.0.9",
  "ch.qos.logback" % "logback-classic" % "1.4.11" % Runtime excludeAll(
    "org.slf4j" % "slf4j-api"
  ),

  "org.scalatest" %% "scalatest" % "3.2.17" % "test"
)
