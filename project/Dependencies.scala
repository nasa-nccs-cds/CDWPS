import sbt._


object Version {
  val mockito   = "1.10.19"
  val scalaTest = "2.2.4"
  val spark = "1.6.3"
  val edas = sys.env.getOrElse("EDAS_VERSION","{UNDEFINED}") + "-SNAPSHOT"
}

object Library {
  val cdm            = "edu.ucar"           % "cdm"             % "4.6.6"
  val clcommon       = "edu.ucar"           % "clcommon"        % "4.6.6"
  val netcdf4        = "edu.ucar"           % "netcdf4"         % "4.6.6"
  val mockitoAll     = "org.mockito"       %  "mockito-all"     % Version.mockito
  val scalaTest      = "org.scalatest"     %% "scalatest"       % Version.scalaTest
  val edas           = "nasa.nccs"         %% "edas"            % Version.edas
  val scalactic      = "org.scalactic"      %% "scalactic"      % "3.0.0"
  val scalatest      = "org.scalatest"      %% "scalatest"      % "3.0.0" % "test"
  val sparkCore      = "org.apache.spark"  %% "spark-core"      % Version.spark
}

object Dependencies {
  import Library._

  val scala = Seq( scalactic, scalatest )

  val EDAS = Seq( edas, sparkCore )

  val netcdf = Seq( cdm, clcommon, netcdf4 )

}
