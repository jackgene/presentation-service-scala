resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.16")

// scoverage
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.9.3")

// JMH
addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.4.3")
