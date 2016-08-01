import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys._

name := """CDWPS"""

organization := "nccs"

version := "1.1-SNAPSHOT"

scalaVersion := "2.11.7"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq( cache, ws, specs2 % Test )

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"
resolvers += "Local Repository" at "~tpmaxwel/.ivy"

libraryDependencies += filters
libraryDependencies ++= Dependencies.scala
libraryDependencies ++= Dependencies.CDS2

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

routesGenerator := InjectedRoutesGenerator

fork in run:= true
javaOptions in run ++= Seq( "-Xmx4000M", "-Xms512M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled", "-XX:+UseConcMarkSweepGC", "-XX:MaxPermSize=800M" )

import java.util.Properties

lazy val cdasProperties = settingKey[Properties]("The cdas properties map")
lazy val cdasPropertiesFile = settingKey[File]("The cdas properties file")
lazy val cdasLocalCollectionsFile = settingKey[File]("The cdas local Collections file")

cdasPropertiesFile :=  baseDirectory.value / "project" / "cdas.properties"

cdasProperties := {
  val prop = new Properties()
  try{ IO.load( prop, cdasPropertiesFile.value ) } catch { case err: Exception => println("No properties file found") }
  prop
}

resolvers += "Local CDAS Repository" at "file:///" + getPublishDir( cdasProperties.value ).toString

def getCacheDir( properties: Properties ): File =
  sys.env.get("CDAS_CACHE_DIR") match {
    case Some(cache_dir) => file(cache_dir)
    case None =>
      val home = file(System.getProperty("user.home"))
      val cache_dir = properties.getProperty("cdas.cache.dir", "")
      if (cache_dir.isEmpty) { home / ".cdas" / "cache" } else file( cache_dir )
  }

def getPublishDir( properties: Properties ): File =
  sys.env.get("SBT_PUBLISH_DIR") match {
    case Some(cache_dir) => file(cache_dir)
    case None =>
      val home = file(System.getProperty("user.home"))
      val cache_dir = properties.getProperty("cdas.publish.dir", "")
      if (cache_dir.isEmpty) { home / ".cdas" / "cache" } else file( cache_dir )
  }

lazy val cdas_cache_dir = settingKey[File]("The CDAS cache directory.")

cdas_cache_dir := {
  val cache_dir = getCacheDir( cdasProperties.value )
  cache_dir.mkdirs()
  cdasProperties.value.put( "cdas.cache.dir", cache_dir.getAbsolutePath )
  try{ IO.write( cdasProperties.value, "", cdasPropertiesFile.value ) } catch { case err: Exception => println("Error writing to properties file: " + err.getMessage ) }
  cache_dir
}

cdasLocalCollectionsFile :=  {
  val collections_file = cdas_cache_dir.value / "local_collections.xml"
  if( !collections_file.exists ) { xml.XML.save( collections_file.getAbsolutePath, <collections></collections> ) }
  collections_file
}

unmanagedClasspath in Compile += cdas_cache_dir.value
unmanagedClasspath in Runtime += cdas_cache_dir.value
unmanagedClasspath in Test += cdas_cache_dir.value

// dependencyOverrides ++= Set( "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4" )
// val appDependencies = Seq( "org.scalatestplus" % "play_2.10" % "1.0.0" % "test" )
// releaseSettings
// scalariformSettings





