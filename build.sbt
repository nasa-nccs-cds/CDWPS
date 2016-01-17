import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys._

name := """cdas"""
organization := "nccs"
version := "1.0-SNAPSHOT"
scalaVersion := "2.11.7"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

unmanagedSourceDirectories in Compile += baseDirectory.value / "src/main/scala"
libraryDependencies ++= Seq( cache, ws, specs2 % Test )
libraryDependencies ++= Dependencies.sparkAkkaHadoop
dependencyOverrides ++= Set( "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4" )
val appDependencies = Seq( "org.scalatestplus" % "play_2.10" % "1.0.0" % "test" )
releaseSettings
scalariformSettings
ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
routesGenerator := InjectedRoutesGenerator


