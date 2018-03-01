name := "bank-account"

version := "1.0.0-SNAPSHOT"

libraryDependencies += guice
libraryDependencies += "net.codingwell" %% "scala-guice" % "4.1.1"
libraryDependencies += "net.logstash.logback" % "logstash-logback-encoder" % "4.11"
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test

lazy val root = (project in file(".")).enablePlugins(PlayScala)
