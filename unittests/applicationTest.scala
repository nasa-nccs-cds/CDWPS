import controllers.WPS
import nasa.nccs.utilities.Loggable
import play.api.inject.DefaultApplicationLifecycle

class applicationTest {
  def run( ): Unit = {
    val wps = new WPS( new DefaultApplicationLifecycle() )
    val datainputs=
      """[  variable=[{"uri": "https://dataserver.nccs.nasa.gov/thredds/dodsC/bypass/CREATE-IP//reanalysis/MERRA2/mon/atmos/tas.ncml", "name": "tas:v0", "domain": "d0"}],
        |   domain=[{"name": "d0", "lat": {"start": 50, "end": 50, "system": "values"}, "lon": {"start": 100, "end": 100, "system": "values"}}],
        |   operation=[{"name": "xarray.subset", "input": "v0"}] ]""".replace(" ","")
    val response = wps.execute( "requestId", "identifier", datainputs, "", "", "" )
    print( response.toString() )
  }
}

object ApplicationTest extends Loggable {
  def main(args: Array[String]): Unit = {
    val test = new applicationTest()
    test.run()
  }
}
