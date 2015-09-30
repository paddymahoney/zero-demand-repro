lazy val root = project.in(file(".")).settings(
  scalaVersion := "2.11.7",
  libraryDependencies ++= Seq(
    "org.scalatest" % "scalatest_2.11" % "2.2.4" % "test",
    "com.typesafe.akka" % s"akka-stream-testkit-experimental_2.11" % "1.0" % "test",
    "com.typesafe.akka" % "akka-stream-experimental_2.11" % "1.0",
    "com.typesafe.akka" % "akka-http-core-experimental_2.11" % "1.0",
    "com.typesafe.akka" % "akka-http-experimental_2.11" % "1.0",
    "com.typesafe.akka" % "akka-contrib_2.11" % "2.3.12"))
