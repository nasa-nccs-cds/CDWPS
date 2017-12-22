import play.sbt.PlayImport._
import play.sbt.routes.RoutesKeys._
import com.github.play2war.plugin._

name := """CDWPS"""

organization := "nccs"

val EDAS_VERSION = sys.env.get("EDAS_VERSION").getOrElse("{UNDEFINED}")

version := EDAS_VERSION + "-SNAPSHOT"
scalaVersion := "2.11.8"
organization := "nasa.nccs"

Play2WarPlugin.play2WarSettings

Play2WarKeys.servletVersion := "3.0"

Play2WarKeys.targetName := Some("wps")

Play2WarKeys.explodedJar := false

lazy val root = (project in file(".")).enablePlugins(PlayScala)

libraryDependencies ++= Seq( cache, ws, specs2 % Test )

def getCacheDir(): File = {
  val cdir = sys.env.get("EDAS_CACHE_DIR") match {
    case Some(cache_dir) => file(cache_dir)
    case None =>
      val home = file(System.getProperty("user.home"))
      home / ".cdas" / "cache"
  }
  cdir.mkdirs()
  cdir
}

def getPublishDir(): File = {
  val pdir = sys.env.get("SBT_PUBLISH_DIR") match {
    case Some(cache_dir) => file(cache_dir)
    case None => getCacheDir() / "publish"
  }
  pdir.mkdirs()
  pdir
}

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases"
resolvers += "Local EDAS Repository" at "file:///" + getPublishDir( ).toString
resolvers += "Geotoolkit" at "http://maven.geotoolkit.org/"

dependencyOverrides += Library.jacksonCore
dependencyOverrides += Library.jacksonDatabind
dependencyOverrides += Library.jacksonModule

libraryDependencies += filters
libraryDependencies ++= Dependencies.scala
libraryDependencies ++= Dependencies.EDAS

ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) }

routesGenerator := InjectedRoutesGenerator

fork in run:= true
javaOptions in run ++= Seq( "-Xmx32000M", "-Xms512M", "-Xss1M", "-XX:+CMSClassUnloadingEnabled", "-XX:+UseConcMarkSweepGC", "-XX:MaxPermSize=800M" )

import java.util.Properties

lazy val cdasProperties = settingKey[Properties]("The cdas properties map")
lazy val cdasPropertiesFile = settingKey[File]("The cdas properties file")

cdasPropertiesFile :=  baseDirectory.value / "project" / "cdas.properties"

cdasProperties := {
  val prop = new Properties()
  try{ IO.load( prop, cdasPropertiesFile.value ) } catch { case err: Exception => println("No properties file found") }
  prop
}

lazy val cdas_cache_dir = settingKey[File]("The EDAS cache directory.")
cdas_cache_dir :=  getCacheDir( )

lazy val cdas_publish_dir = settingKey[File]("The EDAS publish directory.")
cdas_publish_dir :=  getPublishDir( )

lazy val cdasLocalCollectionsFile = settingKey[File]("The cdas local Collections file")
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





