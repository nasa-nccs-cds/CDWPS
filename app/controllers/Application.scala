package controllers

import org.slf4j.LoggerFactory
import play.api._
import java.io.File

import nasa.nccs.edas.engine.ExecutionCallback
import java.util.concurrent.PriorityBlockingQueue

import nasa.nccs.esgf.process.TaskRequest
import nasa.nccs.edas.utilities.appParameters
import play.api.Play
import play.api.mvc._
import nasa.nccs.esgf.wps._
import nasa.nccs.utilities.{EDASLogManager, Loggable}
import nasa.nccs.wps.{ResponseSyntax, WPSResponse}
import org.apache.commons.lang.RandomStringUtils

class WPS extends Controller {
  val logger = EDASLogManager.getCurrentLogger;  /* LoggerFactory.getLogger("application") */
  val jobQueue = new PriorityBlockingQueue[Job]()
  val printer = new scala.xml.PrettyPrinter(200, 3)


  def execute(version: String,
              request: String,
              identifier: String,
              service: String,
              storeExecuteResponse: String,
              status: String,
              datainputs: String) = Action {
    try {
      val runargs = Map("responseform" -> "wps","storeExecuteResponse" -> storeExecuteResponse.toLowerCase, "status" -> status.toLowerCase )
      logger.info(s"\n\nWPS EXECUTE: server_address=$server_address, identifier=$identifier, service=$service, runargs=$runargs, datainputs=$datainputs\n\n")
      val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
      val rId: String = RandomStringUtils.random( 6, true, true )
      val job = Job( rId, service, identifier, datainputs, runargs)
      jobQueue.put(job)
      Ok(response).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }
}

class serverRequestManager() extends Thread with Loggable {
  override def run() {
    val config: Map[String, String] = serverConfiguration
    appParameters.setCustomCacheDir( config.getOrElse( "edas.cache.dir", "" ) )
    appParameters.addConfigParams(config)
    val server_address = config.getOrElse( "edas.server.address", "" )
    logger.info( "Starting webProcessManager with server_address = " + server_address )
    val webProcessManager = if( server_address.isEmpty )   { new ProcessManager(config) }
    else                           { new zmqProcessManager(config) }


  }

  def submitJob( processMgr: GenericProcessManager, job: Job ): Unit = try {
    val t0 = System.nanoTime()
    request.toLowerCase match {
      case "getcapabilities" =>
        logger.info("getcapabilities")
        print("getcapabilities")
        Ok(processMgr.getCapabilities(service, identifier, Map("syntax" -> "WPS"))).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      case "describeprocess" =>
        logger.info("describeprocess")
        print("describeprocess")
        Ok(processMgr.describeProcess(service, identifier, Map("syntax" -> "WPS"))).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      case "execute" =>

        val runargs = Map("responseform" -> "wps", "storeExecuteResponse" -> storeExecuteResponse.toLowerCase, "status" -> status.toLowerCase)
        logger.info(s"\n\nWPS EXECUTE: server_address=$server_address, identifier=$identifier, service=$service, runargs=$runargs, datainputs=$datainputs\n\n")
        val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
        val rId: String = RandomStringUtils.random(6, true, true)
        val request = TaskRequest(rId, service, parsed_data_inputs)
        val executionCallback: ExecutionCallback = new ExecutionCallback {
          override def execute(jobId: String, results: WPSResponse): Unit = {}
        }
        val response: xml.Node = processMgr.executeProcess(job, Some(executionCallback))
        logger.info("Completed request '%s' in %.4f sec".format(job.identifier, (System.nanoTime() - t0) / 1.0E9))
        val printer = new scala.xml.PrettyPrinter(200, 3)
        println("---------->>>> Final Result: " + printer.format(response))
        Ok(response).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  } catch {
      case e: BadRequestException =>
      val error_mesage = CDSecurity.sanitize(  e.getMessage + ":\n" + e.getStackTrace.map( _.toString ).mkString("\n") )
      BadRequest( <error type="ImproperlyFormedRequest"> {"<![CDATA[\n " + error_mesage + "\n]]>"} </error> ).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    case e: NotAcceptableException =>
      val error_mesage = CDSecurity.sanitize(  e.getMessage + ":\n" + e.getStackTrace.map( _.toString ).mkString("\n") )
      NotAcceptable(<error type="UnacceptableRequest"> {"<![CDATA[\n " + error_mesage + "\n]]>"} </error>).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    case e: Exception =>
      val error_mesage = CDSecurity.sanitize(  e.getMessage + ":\n" + e.getStackTrace.map( _.toString ).mkString("\n") )
      InternalServerError(<error type="InternalServerError"> {"<![CDATA[\n " + error_mesage + "\n]]>"} </error>).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
  }

  def getResultFile(processMgr: GenericProcessManager, id: String, service: String) = Action {
    try {
      processMgr.getResultFilePath(service, id) match {
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

  def getResult(processMgr: GenericProcessManager, id: String, service: String) = Action {
    try {
      val result = processMgr.getResult(service, id, ResponseSyntax.WPS )
      Ok(result).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    } catch {
      case e: Exception =>  InternalServerError(e.getMessage) .withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }

  def getResultStatus(processMgr: GenericProcessManager, id: String, service: String) = Action {
    try {
      val result = processMgr.getResultStatus(service, id, ResponseSyntax.WPS )
      Ok(result).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    } catch {
      case e: Exception =>  InternalServerError(e.getMessage) .withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }


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

}

object parseTest extends App {
  val datainputs = """ [domain=[{"name":"d0","lat":{"start":45,"end":45,"system":"values"},"lon":{"start":30,"end":30,"system":"values"},"lev":{"start":3,"end":3,"system":"indices"}}],variable={"uri":"collection://MERRA/mon/atmos","name":"ta:v0","domain":"d0"},operation=[{ "input":["v0"], "unit":"month", "period":"12" }]  ] """
  val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
  println(parsed_data_inputs)
}
