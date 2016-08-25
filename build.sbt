name := """akka-hikari-sample"""

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(

  "com.typesafe.akka" %% "akka-actor" % "2.4.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.9" % "test",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.9",
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "com.zaxxer" % "HikariCP" % "2.4.7",
  "mysql" % "mysql-connector-java" % "5.1.34",
  "org.slf4j" % "slf4j-simple" % "1.7.21"
)
