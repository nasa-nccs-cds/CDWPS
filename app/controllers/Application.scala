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
        Logger.info(s"WPS EXECUTE: identifier=$identifier, datainputs=$datainputs")
        val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
        val result = webProcessManager.executeProcess(identifier, parsed_data_inputs, runargs)
        result match {
          case Some(p) =>
            val response = p.toXml
            Ok(response)
          case None => NotFound("Unrecognized process")
        }
    }
  }
}

object testWPS extends App {
  val identifier = "CWT.average"
  val datainputs = """[domain={"id":"d0","level":{"start":0,"end":1,"system":"indices"}},variable={"dset":"MERRA/mon/atmos","id":"v0:hur","domain":"d0"},operation="(v0,axis:xy)"]"""
  val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
  val runargs = Map[String, Any]()
  val result = webProcessManager.executeProcess(identifier, parsed_data_inputs, runargs)
  result match {
    case Some(p) => println(p.toXml)
    case None => println("Unrecognized process")
  }
}
