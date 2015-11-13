name := """newshub"""
organization := "org.i40u"
version := "1.0.0"
scalaVersion := "2.11.7"

resolvers += Resolver.sonatypeRepo("public")

val akkaVersion = "2.3.12"
val akkaStreamVersion = "1.0"

libraryDependencies ++= Seq(
  // logging
  "org.slf4j" % "slf4j-api" % "1.7.12",
  "ch.qos.logback" % "logback-classic" % "1.1.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.1.0",
  // akka
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  // akka http
  "com.typesafe.akka" %% "akka-stream-experimental" % akkaStreamVersion,
  "com.typesafe.akka" %% "akka-http-core-experimental" % akkaStreamVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaStreamVersion,
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaStreamVersion,
  // elastic for scala
  "com.sksamuel.elastic4s" %% "elastic4s-core" % "1.7.0",
  "com.sksamuel.elastic4s" %% "elastic4s-jackson" % "1.7.0",
  // ROME RSS parser
  "com.rometools" % "rome" % "1.5.1",
  "com.rometools" % "rome-modules" % "1.5.1",
  // URI util lib
  "com.netaporter" %% "scala-uri" % "0.4.9",
  // joda time
  "joda-time" % "joda-time" % "2.8.2",
  // jackson
  "com.fasterxml.jackson.datatype" % "jackson-datatype-joda" % "2.5.3",
  // asynchronous http client
  "com.ning" % "async-http-client" % "1.9.29"
)

// using goose from here https://github.com/warrd/goose-fork
libraryDependencies += "com.gravity" %% "goose" % "2.1.25-SNAPSHOT" exclude("org.slf4j", "slf4j-simple")

// fixes warns from SBT that says "Multiple dependencies with same organization/name but diff versions."
libraryDependencies ++= Seq(
  "org.scala-lang" % "scala-reflect" % "2.11.7",
  "org.scala-lang.modules" % "scala-xml_2.11" % "1.0.4"
)

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "2.2.5" % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2.2" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-http-testkit-experimental" % akkaStreamVersion % "test"
)
