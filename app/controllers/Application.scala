package controllers

import play.api._
import play.api.mvc._
import nccs.process.webProcessManager
import utilities.wpsObjectParser

class Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your application is ready."))
  }
}

class WPS extends Controller {

  def demo = Action {
    Ok(views.html.demo())
  }

  def execute(version: String,
    request: String,
    identifier: String,
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
        case "execute" =>
          val runargs = Map[String, Any]("responseform" -> responseform, "storeexecuteresponse" -> storeexecuteresponse, "status" -> status)
          val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
          val result = webProcessManager.executeProcess(identifier, parsed_data_inputs, runargs)
          result match {
            case Some(p) => Ok(p.toString)
            case None => NotFound("Unrecognized process")
          }
      }
    }
  }

