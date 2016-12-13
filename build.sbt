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
resolvers += "Local CDAS Repository" at "file:///" + getPublishDir( ).toString
resolvers += "Geotoolkit" at "http://maven.geotoolkit.org/"

val fasterxml = "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.4"

dependencyOverrides ++= Set( fasterxml )

libraryDependencies += filters
libraryDependencies ++= Dependencies.scala
libraryDependencies ++= Dependencies.CDS2

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

routesGenerator := InjectedRoutesGenerator

fork in run:= true
javaOptions in run ++= Seq( "-Xmx32000M", "-Xms512M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled", "-XX:+UseConcMarkSweepGC", "-XX:MaxPermSize=800M" )

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

def getCacheDir(): File =
  sys.env.get("CDAS_CACHE_DIR") match {
    case Some(cache_dir) => file(cache_dir)
    case None =>
      val home = file(System.getProperty("user.home"))
      home / ".cdas" / "cache"
  }

def getPublishDir(): File = {
  val pdir = sys.env.get("SBT_PUBLISH_DIR") match {
    case Some(cache_dir) => file(cache_dir)
    case None => getCacheDir() / "publish"
  }
  pdir.mkdirs()
  pdir
}

lazy val cdas_cache_dir = settingKey[File]("The CDAS cache directory.")

cdas_cache_dir := {
  val cache_dir = getCacheDir( )
  cache_dir.mkdirs()
  cache_dir
}

lazy val cdas_publish_dir = settingKey[File]("The CDAS publish directory.")

cdas_publish_dir := {
  val pdir = getPublishDir( )
  pdir.mkdirs()
  pdir
}

cdasLocalCollectionsFile :=  {
  val collections_file = cdas_cache_dir.value / "local_collections.xml"
  if( !collections_file.exists ) { xml.XML.save( collections_file.getAbsolutePath, <collections></collections> ) }
  collections_file
}

unmanagedClasspath in Compile += cdas_publish_dir.value
unmanagedClasspath in Runtime += cdas_publish_dir.value
unmanagedClasspath in Test += cdas_publish_dir.value


// val appDependencies = Seq( "org.scalatestplus" % "play_2.10" % "1.0.0" % "test" )
// releaseSettings
// scalariformSettings





