// The Typesafe repository
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.12")

// Scala formatting: "sbt scalafmt"
addSbtPlugin("com.lucidchart" % "sbt-scalafmt" % "1.12")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")