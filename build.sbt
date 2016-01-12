name := """cdas"""
organization := "nccs"
version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

unmanagedSourceDirectories in Compile += baseDirectory.value / "src/main/scala"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2 % Test
//  "org.scala-lang.modules" %% "scala-xml" % "1.0.3",
//  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3"
)

val appDependencies = Seq(
  "org.scalatestplus" % "play_2.10" % "1.0.0" % "test"
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator
