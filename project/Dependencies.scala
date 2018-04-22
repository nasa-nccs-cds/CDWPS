import sbt._


object Version {
  val mockito   = "1.10.19"
  val scalaTest = "2.2.4"
  val ucar = "4.6.8"
  val spark = "2.2.1"
  val jackson = "2.6.5"
  val edas = sys.env.getOrElse("EDAS_VERSION","{UNDEFINED}") + "-SNAPSHOT"
}

object Library {
  val cdm            = "edu.ucar"           % "cdm"             % Version.ucar
  val clcommon       = "edu.ucar"           % "clcommon"        % Version.ucar
  val netcdf4        = "edu.ucar"           % "netcdf4"         % Version.ucar
  val mockitoAll     = "org.mockito"       %  "mockito-all"     % Version.mockito
  val scalaTest      = "org.scalatest"     %% "scalatest"       % Version.scalaTest
  val edas           = "nasa.nccs"         %% "edas"            % Version.edas
  val scalactic      = "org.scalactic"      %% "scalactic"      % "3.0.0"
  val scalatest      = "org.scalatest"      %% "scalatest"      % "3.0.0" % "test"
  val sparkCore      = "org.apache.spark"  %% "spark-core"      % Version.spark
  val play           = "com.typesafe.play"  %% "play"           % "2.6.4"
  val playServer     = "com.typesafe.play"  %% "play-server"    % "2.6.4"
  val playWS         = "com.typesafe.play"  %% "play-ws"        % "2.6.4"

  val jacksonCore = "com.fasterxml.jackson.core" % "jackson-core" % Version.jackson
  val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % Version.jackson
  val jacksonModule = "com.fasterxml.jackson.module" % "jackson-module-scala_2.10" % Version.jackson
}

object Dependencies {
  import Library._

  val scala = Seq( scalactic, scalatest )

  val EDAS = Seq( edas, sparkCore )

  val playFramework = Seq( play )

  val netcdf = Seq( cdm, clcommon, netcdf4 )

}
