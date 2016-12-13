import sbt._

object Version {
  val mockito   = "1.10.19"
  val scala     = "2.11.7"
  val scalaTest = "2.2.4"
}

object Library {
  val cdm            = "edu.ucar"           % "cdm"             % "4.6.6"
  val clcommon       = "edu.ucar"           % "clcommon"        % "4.6.6"
  val netcdf4        = "edu.ucar"           % "netcdf4"         % "4.6.6"
  val mockitoAll     = "org.mockito"       %  "mockito-all"     % Version.mockito
  val scalaTest      = "org.scalatest"     %% "scalatest"       % Version.scalaTest
  val scalaxml       = "org.scala-lang.modules" %% "scala-xml"  % "1.0.3"
  val scalaparser    = "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.3"
  val cds2           = "nasa.nccs"         %% "cdas2"       % "1.2-SNAPSHOT"
  val scalactic      = "org.scalactic" %% "scalactic" % "3.0.0"
  val scalatest      = "org.scalatest" %% "scalatest" % "3.0.0" % "test"
}

object Dependencies {
  import Library._

  val scala = Seq( scalaxml, scalaparser, scalactic, scalatest )

  val CDS2 = Seq( cds2 )

  val netcdf = Seq( cdm, clcommon, netcdf4 )

}
