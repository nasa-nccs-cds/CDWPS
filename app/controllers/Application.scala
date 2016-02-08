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
    try {
      request.toLowerCase match {
        case "getcapabilities" =>
          Ok(webProcessManager.listProcesses(service))
        case "describeprocess" =>
          Ok(webProcessManager.describeProcess(service, identifier))
        case "execute" =>
          val runargs = Map[String, Any]("responseform" -> responseform, "storeexecuteresponse" -> storeexecuteresponse, "status" -> status)
          logger.info(s"WPS EXECUTE: identifier=$identifier, service=$service, runargs=$runargs, datainputs=$datainputs")
          val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
          val response = webProcessManager.executeProcess(service, identifier, parsed_data_inputs, runargs)
          Ok(response)
      }
    } catch {
      case e: BadRequestException => BadRequest(<error type="ImproperlyFormedRequest"> {"<![CDATA[\n " + e.getMessage + "\n]]>"} </error>)
      case e: NotAcceptableException => NotAcceptable(<error type="UnacceptableRequest"> {"<![CDATA[\n " + e.getMessage + "\n]]>"} </error>)
      case e: Exception => InternalServerError(<error type="InternalServerError"> {"<![CDATA[\n " + e.getMessage + "\n]]>"} </error>)
    }
  }
}


