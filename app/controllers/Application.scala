package controllers

import org.slf4j.LoggerFactory
import play.api._
import play.api.mvc._
import process.webProcessManager
import process.exceptions._
import utilities.parsers.{ wpsObjectParser, BadRequestException }

class Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your application is ready."))
  }
}

class WPS extends Controller {
  val logger = LoggerFactory.getLogger(classOf[WPS])

  def demo = Action {
    Ok(views.html.demo())
  }

  def execute(version: String,
    request: String,
    identifier: String,
    service: String,
    responseform: String,
    storeexecuteresponse: Boolean,
    status: Boolean,
    datainputs: String) = Action {
    request.toLowerCase match {
      case "getcapabilities" =>
        Ok(webProcessManager.listProcesses)
      case "describeprocess" =>
        webProcessManager.describeProcess(identifier) match {
          case Some(p) => Ok(p)
          case None => NotFound("Unrecognized process")
        }
      case "execute" => {
        val runargs = Map[String, Any]("responseform" -> responseform, "storeexecuteresponse" -> storeexecuteresponse, "status" -> status)
        logger.info(s"WPS EXECUTE: identifier=$identifier, service=$service, runargs=$runargs, datainputs=$datainputs")
        try {

          val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
          val response = webProcessManager.executeProcess(service, identifier, parsed_data_inputs, runargs)
          Ok(response)
        } catch {
          case e: BadRequestException => BadRequest(<error type="ImproperlyFormedRequest">
                                                      { "<![CDATA[\n " + e.getMessage + "\n]]>" }
                                                    </error>)
          case e: NotAcceptableException => NotAcceptable(<error type="UnacceptableRequest">
                                                            { "<![CDATA[\n " + e.getMessage + "\n]]>" }
                                                          </error>)
          case e: Exception => InternalServerError(<error type="InternalServerError">
                                                     { "<![CDATA[\n " + e.getMessage + "\n]]>" }
                                                   </error>)
        }
      }
    }
  }
}

//object testWPS extends App {
//  val identifier = "CWT.average"
//  val datainputs = """[domain=[{"id":"d0","level":{"start":0,"end":1,"system":"indices"}},{"id":"d1","level":{"start":1,"end":2,"system":"indices"}}],variable=[{"dset":"MERRA/mon/atmos","id":"hur:v0","domain":"d0"},{"dset":"MERRA/mon/atmos","id":"tmp:v1","domain":"d1"}],operation=["CWT.average(v0,axis:xy)","CWT.anomaly(v1,axis:xy)"]]"""
//  val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
//  val runargs = Map[String, Any]()
//  val result = webProcessManager.executeProcess(identifier, parsed_data_inputs, runargs)
//  result match {
//    case Some(p) => println(p)
//    case None => println("Unrecognized process")
//  }
//}

