name := "AprendiendoAkkaHttp"

version := "1.0"

resolvers ++= Seq(
  "SpinGo OSS" at "http://spingo-oss.s3.amazonaws.com/repositories/releases"
)

scalaVersion := "2.12.1"
val opRabbitVersion = "2.0.0-rc1"
val circeV = "0.7.0"
libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.4.17",
  "com.typesafe.akka" %% "akka-actor" % "2.4.17",
  "com.typesafe.akka" %% "akka-stream-testkit" % "2.4.17" % "test",

  "org.scalatest" %% "scalatest" % "3.0.0" % "test",

  "com.typesafe.akka" %% "akka-http-core" % "10.0.5",
  "com.typesafe.akka" %% "akka-http" % "10.0.5",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.5",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.5",
  "de.heikoseeberger" %% "akka-http-circe" % "1.13.0",

"com.spingo" %% "op-rabbit-core" % opRabbitVersion,
  "com.spingo" %% "op-rabbit-play-json" % opRabbitVersion,
  "com.spingo" %% "op-rabbit-akka-stream" % opRabbitVersion,

  "io.circe" %% "circe-core" % circeV,
  "io.circe" %% "circe-parser" % circeV,
  "io.circe" %% "circe-generic" % circeV
)