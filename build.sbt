import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys._

name := """BDWPS"""

organization := "nccs"

version := "1.0-SNAPSHOT"

scalaVersion := "2.11.7"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq( cache, ws, specs2 % Test )

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"

libraryDependencies += filters
libraryDependencies ++= Dependencies.scala
libraryDependencies ++= Dependencies.CDS2

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

routesGenerator := InjectedRoutesGenerator

// dependencyOverrides ++= Set( "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4" )
// val appDependencies = Seq( "org.scalatestplus" % "play_2.10" % "1.0.0" % "test" )
// releaseSettings
// scalariformSettings





