package controllers


import play.api._
import java.io.File
import play.api.Play.current
import scala.xml.XML
import nasa.nccs.edas.engine.ExecutionCallback
import java.util.concurrent.{PriorityBlockingQueue, TimeUnit}
import scala.concurrent.Future
import javax.inject._
import play.api.inject.ApplicationLifecycle
import nasa.nccs.edas.utilities.appParameters
import scala.collection.concurrent.TrieMap
import play.api.Play
import play.api.mvc._
import nasa.nccs.esgf.wps.{GenericProcessManager, Job, _}
import scala.concurrent.ExecutionContext.Implicits.global
import nasa.nccs.utilities.{EDASLogManager, Loggable}
import nasa.nccs.wps._
import org.apache.commons.lang.RandomStringUtils


object StatusValue extends Enumeration { val QUEUED, EXECUTING, COMPLETED, UNDEFINED = Value }
case class WPSJobStatus( job: Job ) {
  private var _status: StatusValue.Value = StatusValue.QUEUED
  def setStatus( status: StatusValue.Value ): Unit = { _status = status }
  def getStatus: StatusValue.Value = { _status }
}

class WPS @Inject() (lifecycle: ApplicationLifecycle) extends Controller with Loggable {
  val play_app = current;
  val printer = new scala.xml.PrettyPrinter(200, 3)
  logger.info( "\n ------------------------- EDASW: Application STARTUP ----------------------------------- \n" )
  val serverRequestManager = new ServerRequestManager()
  serverRequestManager.initialize()
  lifecycle.addStopHook( { () => term() } )

  def term(): Future[Unit] = {
    try {
      logger.info("\n ------------------------- EDASW: Application SHUTDOWN ----------------------------------- \n")
      serverRequestManager.term()
      logger.close()
      Future.successful()
    } catch { case err: Exception => Future.failed(err) }
  }

  def execute(request: String, identifier: String, datainputs: String) = Action {
    try {
      val storeExecuteResponse: String = "true";
      val status: String = "true";
      request.toLowerCase match {
        case "getcapabilities" =>
          Ok( serverRequestManager.getCapabilities( identifier) ).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
        case "describeprocess" =>
          Ok( serverRequestManager.describeProcess( identifier) ).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
        case "execute" =>
          val runargs = Map("responseform" -> "wps", "storeExecuteResponse" -> storeExecuteResponse.toLowerCase, "status" -> status.toLowerCase)
          val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
          val requestId: String = RandomStringUtils.random(6, true, true)
          val job = Job( requestId, identifier, datainputs, runargs )
          serverRequestManager.addJob(job)
          val response = createResponse( requestId )
          Ok(response).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      }
    } catch {
      case e: BadRequestException =>
        val error_mesage = CDSecurity.sanitize(e.getMessage + ":\n" + e.getStackTrace.map(_.toString).mkString("\n"))
        BadRequest(<error type="ImproperlyFormedRequest">
          {"<![CDATA[\n " + error_mesage + "\n]]>"}
        </error>).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      case e: NotAcceptableException =>
        val error_mesage = CDSecurity.sanitize(e.getMessage + ":\n" + e.getStackTrace.map(_.toString).mkString("\n"))
        NotAcceptable(<error type="UnacceptableRequest">
          {"<![CDATA[\n " + error_mesage + "\n]]>"}
        </error>).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
      case e: Exception =>
        val error_mesage = CDSecurity.sanitize(e.getMessage + ":\n" + e.getStackTrace.map(_.toString).mkString("\n"))
        InternalServerError(<error type="InternalServerError">
          {"<![CDATA[\n " + error_mesage + "\n]]>"}
        </error>).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }

  def createResponse( responseId: String ): xml.Node = {
    val response = new AsyncExecutionResult("edas", List.empty[WPSProcess], responseId)
    response.toXml( serverRequestManager.response_syntax )
  }

  def getResultStatus(responseId: String ) = Action {
    try {
      val result = serverRequestManager.getJobStatus(responseId)
      Ok(result).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    } catch {
      case e: Exception => InternalServerError(e.getMessage).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }

  def getResultFile(id: String, service: String) = Action {
    try {
      serverRequestManager.getResultFilePath(service, id) match {
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
      val result = serverRequestManager.getResult(service, id, ResponseSyntax.WPS )
      Ok(result).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    } catch {
      case e: Exception =>  InternalServerError(e.getMessage) .withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }

}

class ServerRequestManager extends Thread with Loggable {
  val jobQueue = new PriorityBlockingQueue[String]()
  val jobDirectory = TrieMap.empty[String,WPSJobStatus]
  private var _active = true;
  val capabilitiesCache = TrieMap.empty[String,xml.Node]
  val processesCache = TrieMap.empty[String,xml.Node]
  val responseCache = TrieMap.empty[String,xml.Node]
  var active: Boolean = true;
  val config: Map[String, String] = serverConfiguration
  appParameters.setCustomCacheDir( config.getOrElse( "edas.cache.dir", "" ) )
  appParameters.addConfigParams(config)
  val server_address = config.getOrElse( "edas.server.address", "" )
  val response_syntax: ResponseSyntax.Value = ResponseSyntax.fromString( config.getOrElse( "edas.response.syntax", "wps" ) )
  protected var processManager: Option[GenericProcessManager] = None

  def term() = {
    active = false;
    processManager.map( _.term() )
  }

  def getResultFilePath( service: String, resultId: String ): Option[String] = processManager match {
    case Some( procMgr ) => procMgr.getResultFilePath( service, resultId )
    case None => throw new Exception( "Attempt to access undefined ProcessManager")
  }
  def getResult( service: String, resultId: String, response_syntax: ResponseSyntax.Value ): xml.Node= processManager match {
    case Some( procMgr ) => procMgr.getResult( service, resultId, response_syntax )
    case None => throw new Exception( "Attempt to access undefined ProcessManager")
  }
  def getResultStatus( service: String, resultId: String, response_syntax: ResponseSyntax.Value ): xml.Node= processManager match {
    case Some( procMgr ) => procMgr.getResultStatus( service, resultId, response_syntax )
    case None => throw new Exception( "Attempt to access undefined ProcessManager")
  }

  def deactivate = { active = false; }

  def addJob( job: Job ): Unit = {
    jobDirectory += ( job.requestId -> WPSJobStatus(job) )
    jobQueue.put( job.requestId )
    logger.info( s"EDASW:Added job ${job.requestId} to job queue, nJobs = ${jobQueue.size()}"  )
  }

  def initialize(): Unit = {
    setDaemon(true)
    start()
//    getCapabilities("")
//    getCapabilities("col")
  }

  def getResponseR( responseId: String, timeout_sec: Int, current_time_msec: Long = 0L ): xml.Node = {
    val sleeptime_ms = 100L
    val response = if( current_time_msec >= timeout_sec * 1000 ) {
      <error type="InternalServerError" rid={responseId}>  Timed out waiting for response </error>
    } else {
      responseCache.get(responseId) match {
        case Some(response) => return response
        case None =>
          Thread.sleep(sleeptime_ms)
          getResponseR(responseId, timeout_sec, current_time_msec + sleeptime_ms)
      }
    }
    val message = response.toString
    logger.info( s"EDASW::getResponse($responseId), Sample: ${message.substring(0,Math.min(0,message.length))}" )
    response
  }

  def getResponse( responseId: String, timeout_sec: Int ): xml.Node = {
    val sleeptime_ms = 100L
    val timeout_ms =  timeout_sec * 1000
    var  current_time_msec: Long = 0L
    logger.info(s"EDASW::getResponse($responseId): Waiting ")
    while( current_time_msec < timeout_ms) {
      responseCache.get(responseId) match {
        case Some(response) =>
          val raw_message = response.toString
          val message = insertParameterRefs( raw_message )
          logger.info( s"EDASW::getResponse($responseId), Sample: ${message.substring(0,Math.min(0,message.length))}" )
          return  XML.loadString(message)
        case None =>
          Thread.sleep(sleeptime_ms)
          current_time_msec = current_time_msec + sleeptime_ms
          logger.info(".",false)
      }
    }
    logger.info(s"EDASW::getResponse($responseId): Timed Out, current time = ${current_time_msec} ms, responses = {${responseCache.keys.mkString(", ")}}")
    <error type="InternalServerError">"Timed out waiting for response: " + responseId</error>
  }

  def insertParameterRefs( message: String ): String = {
    val pattern = "${[A-Z0-9a-z._]+}".r
    var newMessage = message
    pattern.findAllIn( message ).foreach( parmRef => {
      val parm = parmRef.substring(2,parmRef.length-1)
      appParameters(parm) match {
        case Some( pval ) => newMessage = newMessage.replaceAllLiterally(parmRef,pval)
        case None => logger.warn( s"Can't find parameter '${parm}' in application configuration.")
      }
    })
    newMessage
  }


  def executeJob( job: Job, timeout_sec: Int = 180 ): xml.Node = {
    jobDirectory += ( job.requestId -> WPSJobStatus(job) )
    logger.info( "EDASW::executeJob: " + job.requestId  )
    jobQueue.put( job.requestId )
    getResponse( job.requestId, 180 )
  }

  def updateJobStatus( requestId: String, status: StatusValue.Value ): Job = {
    logger.info( "EDASW::updateJobStatus: " + requestId + ", status = " + status.toString )
    jobDirectory.get( requestId ) match {
      case Some( jobStatus ) =>
        jobStatus.setStatus( status )
        jobStatus.job
      case None => throw new Exception( "Attempt to set status on non-existent job: " + requestId + ", jobs = " + jobDirectory.keys.mkString(", ") )
    }
  }

  def getCapabilities( identifier: String ): xml.Node = {
    capabilitiesCache.get( identifier ) match {
      case Some( cap ) => cap
      case None =>
        val cap = executeJob( new Job( "getcapabilities:" + identifier, identifier ) )
        if( !cap.label.toLowerCase.contains("error") ) { capabilitiesCache.put( identifier, cap ) }
        cap
    }
  }

  def describeProcess( identifier: String ): xml.Node = {
    processesCache.getOrElseUpdate( identifier, executeJob( new Job( "describeprocess:" + identifier, identifier ) ) )
  }


  def getJobStatus( requestId: String ): xml.Elem = {
    val status = jobDirectory.get( requestId ) match {
      case Some( jobStatus ) =>
        jobStatus.getStatus
      case None =>
        logger.error( "Attempt to set status on non-existent job: " + requestId + ", jobs = " + jobDirectory.keys.mkString(", ") )
        StatusValue.UNDEFINED
    }
    new WPSExecuteStatus( "WPS", status.toString, requestId ).toXml( response_syntax )
  }

  override def run() {
    logger.info( "EDASW: Starting webProcessManager with server_address = " + server_address + ", EDAS libs logging to: " + EDASLogManager.getCurrentLogger().logFilePath.toString )
    processManager = Some( if( server_address.isEmpty ) { new ProcessManager(config) } else { new zmqProcessManager(config) } )
    try {
      while ( active ) {
        logger.info( "EDASW::Polling job queue: " + jobQueue.toString )
        Option( jobQueue.poll( 1, TimeUnit.MINUTES ) ) match {
          case Some( jobId ) =>
            logger.info( "EDASW::Popped job for exec: " + jobId )
            val result = submitJob( processManager.get, jobId )
          case None => logger.info( s"EDASW:: Looking for jobs in queue, nJobs = ${jobQueue.size()}" )
        }
      }
    } catch {
      case exc: InterruptedException => return
    }
  }

  def submitJob( processMgr: GenericProcessManager, jobId: String ): xml.Node = try {
    val t0 = System.nanoTime()
    val job = updateJobStatus( jobId, StatusValue.EXECUTING )
    job.requestId.toLowerCase match {
      case jobId if jobId.startsWith("getcapabilities") =>
        jobCompleted( jobId, processMgr.getCapabilities( "cds2", job.identifier, job.runargs ) )
      case jobId if jobId.startsWith("describeprocess") =>
        jobCompleted( jobId, processMgr.describeProcess( "cds2", job.identifier, job.runargs ) )
      case _ =>
        logger.info (s"\n\nWPS EXECUTE: identifier=${job.identifier}, datainputs=${job.datainputs}\n\n")
        val parsed_data_inputs = wpsObjectParser.parseDataInputs (job.datainputs)
        val executionCallback: ExecutionCallback = new ExecutionCallback {
          override def execute ( response: WPSResponse ): Unit = {
            val responseId = jobId.split('-').last
            jobCompleted( responseId, response.toXml( response_syntax ) )
          }
        }
        val response: xml.Node = processMgr.executeProcess( job, Some (executionCallback) )
        logger.info ("Completed request '%s' in %.4f sec".format (job.identifier, (System.nanoTime () - t0) / 1.0E9) )
        response
    }
  }

  def jobCompleted( jobId: String, results: xml.Node ): xml.Node  = {
    responseCache += ( jobId -> results )
    updateJobStatus ( jobId, StatusValue.COMPLETED )
    results
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
      case e: Exception => Map.empty[String, String]
    }
  }

}

object parseTest extends App {
  val datainputs = """ [domain=[{"name":"d0","lat":{"start":45,"end":45,"system":"values"},"lon":{"start":30,"end":30,"system":"values"},"lev":{"start":3,"end":3,"system":"indices"}}],variable={"uri":"collection://MERRA/mon/atmos","name":"ta:v0","domain":"d0"},operation=[{ "input":["v0"], "unit":"month", "period":"12" }]  ] """
  val parsed_data_inputs = wpsObjectParser.parseDataInputs(datainputs)
  println(parsed_data_inputs)
}
