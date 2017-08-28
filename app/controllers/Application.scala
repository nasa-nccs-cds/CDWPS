package controllers

import org.slf4j.LoggerFactory
import play.api._
import java.io.File

import play.api.Play
import play.api.mvc._
import nasa.nccs.esgf.wps.{BadRequestException, CDSecurity, NotAcceptableException, ProcessManager, wpsObjectParser, zmqProcessManager}
import nasa.nccs.utilities.EDASLogManager
import nasa.nccs.wps.ResponseSyntax

class WPS extends Controller {
  val logger = EDASLogManager.getCurrentLogger;  /* LoggerFactory.getLogger("application") */
  val config = serverConfiguration
  val server_address = config.getOrElse( "edas.server.address", "" )
  logger.info( "Starting webProcessManager with server_address = " + server_address )
  val webProcessManager = if( server_address.isEmpty )   { new ProcessManager(config) }
                          else                           { new zmqProcessManager(config) }
  val printer = new scala.xml.PrettyPrinter(200, 3)

  def serverConfiguration: Map[String, String] = {
    try {
      val config = Play.current.configuration
      def get_config_value(key: String): Option[String] = {
        try {
          config.getString(key)
        } catch {
          case ex: Exception => None
        }
      }
      val map_pairs = for (key <- config.keys; value = get_config_value(key); if value.isDefined)
        yield (key -> value.get.toString)
      Map[String, String](map_pairs.toSeq: _*)
    } catch {
      case e: Exception =>
        Map[String, String]("wps.results.dir" -> "~/.wps/results")
    }
  }

  def getResultFile(id: String, service: String) = Action {
    try {
      webProcessManager.getResultFilePath(service, id) match {
        case Some(resultFilePath: String) =>
          logger.info(s"WPS getResult: resultFilePath=$resultFilePath")
          Ok.sendFile(new File(resultFilePath)).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
        case None =>
          NotFound("Result not yet available").withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      }
    } catch {
      case e: Exception =>
        InternalServerError(e.getMessage)
          .withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }

  def getResult(id: String, service: String) = Action {
    try {
      val result = webProcessManager.getResult(service, id, ResponseSyntax.WPS )
      Ok(result).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    } catch {
      case e: Exception =>  InternalServerError(e.getMessage) .withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }

  def getResultStatus(id: String, service: String) = Action {
    try {
      val result = webProcessManager.getResultStatus(service, id, ResponseSyntax.WPS )
      Ok(result).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    } catch {
      case e: Exception =>  InternalServerError(e.getMessage) .withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }

  def execute(version: String,
              request: String,
              identifier: String,
              service: String,
              storeExecuteResponse: String,
              status: String,
              datainputs: String) = Action {
    try {
      request.toLowerCase match {
        case "getcapabilities" =>
          logger.info("getcapabilities")
          print("getcapabilities")
          Ok(webProcessManager.getCapabilities(service, identifier, Map("syntax"->"WPS"))).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
        case "describeprocess" =>
          logger.info("describeprocess")
          print("describeprocess")
          Ok(webProcessManager.describeProcess(service, identifier, Map("syntax"->"WPS"))).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
        case "execute" =>
          val t0 = System.nanoTime()
          val runargs = Map("responseform" -> "wps","storeExecuteResponse" -> storeExecuteResponse.toLowerCase, "status" -> status.toLowerCase )
          logger.info(s"\n\nWPS EXECUTE: identifier=$identifier, service=$service, runargs=$runargs, datainputs=$datainputs\n\n")
          val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
          val response: xml.Node = webProcessManager.executeProcess(service, identifier, datainputs, parsed_data_inputs, runargs)
          logger.info("Completed request '%s' in %.4f sec".format(identifier, (System.nanoTime() - t0) / 1.0E9))
          val printer = new scala.xml.PrettyPrinter(200, 3)
          println("---------->>>> Final Result: " + printer.format(response))
          Ok(response).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      }
    } catch {
      case e: BadRequestException =>
        val error_mesage = CDSecurity.sanitize(  e.getMessage + ":\n" + e.getStackTrace.map( _.toString ).mkString("\n") )
        BadRequest(
          <error type="ImproperlyFormedRequest"> {"<![CDATA[\n " + error_mesage + "\n]]>"} </error>).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      case e: NotAcceptableException =>
        val error_mesage = CDSecurity.sanitize(  e.getMessage + ":\n" + e.getStackTrace.map( _.toString ).mkString("\n") )
        NotAcceptable(<error type="UnacceptableRequest"> {"<![CDATA[\n " + error_mesage + "\n]]>"} </error>).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      case e: Exception =>
        val error_mesage = CDSecurity.sanitize(  e.getMessage + ":\n" + e.getStackTrace.map( _.toString ).mkString("\n") )
        InternalServerError(<error type="InternalServerError"> {"<![CDATA[\n " + error_mesage + "\n]]>"} </error>).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }
}

object parseTest extends App {
  val datainputs = """ [domain=[{"name":"d0","lat":{"start":45,"end":45,"system":"values"},"lon":{"start":30,"end":30,"system":"values"},"lev":{"start":3,"end":3,"system":"indices"}}],variable={"uri":"collection://MERRA/mon/atmos","name":"ta:v0","domain":"d0"},operation=[{ "input":["v0"], "unit":"month", "period":"12" }]  ] """
  val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
  println(parsed_data_inputs)
}
