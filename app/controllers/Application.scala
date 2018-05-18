package controllers


import play.api._
import java.io.File

import play.api.Play.current

import scala.xml.{Node, XML}
import nasa.nccs.edas.engine.ExecutionCallback
import java.util.concurrent.{PriorityBlockingQueue, TimeUnit}

import scala.concurrent.Future
import javax.inject._

import play.api.inject.ApplicationLifecycle
import nasa.nccs.edas.utilities.appParameters
import nasa.nccs.esgf.process.TaskRequest
import nasa.nccs.esgf.process.{DataContainer, DomainContainer, OperationContext, UID}

import scala.collection.concurrent.TrieMap
import play.api.Play
import play.api.mvc._
import nasa.nccs.esgf.wps.{GenericProcessManager, Job, _}

import scala.concurrent.ExecutionContext.Implicits.global
import nasa.nccs.utilities.{EDASLogManager, Loggable, cdsutils}
import nasa.nccs.wps._
import org.apache.commons.lang.RandomStringUtils
import org.joda.time.DateTime

import scala.collection.mutable

object Util {
  def sample( obj: Any, maxLen: Int = 500  ): String = {
    val str = obj.toString
    str.substring( 0, math.min( maxLen, str.length ) )
  }
}

class WPSJob(requestId: String, identifier: String, datainputs: String, private val _runargs: Map[String,String], collectionsNode: xml.Node, _priority: Float) extends Job(requestId, identifier, datainputs, _runargs, _priority) {
  val parsed_data_inputs: Map[String, Seq[Map[String, Any]]] = wpsObjectParser.parseDataInputs(datainputs)
  val uid = UID( requestId )
  val op_spec_list: Seq[Map[String, Any]] = parsed_data_inputs.getOrElse("operation", List())
  val data_list: List[DataContainer] = parsed_data_inputs.getOrElse("variable", List()).flatMap(DataContainer.factory(uid, _, op_spec_list.isEmpty )).toList
  val domain_list: List[DomainContainer] = parsed_data_inputs.getOrElse("domain", List()).map(DomainContainer(_)).toList
  val opSpecs: Seq[Map[String, Any]] = if(op_spec_list.isEmpty) { TaskRequest.getEmptyOpSpecs(data_list) } else { op_spec_list }
  val operation_map: Map[String,OperationContext] = Map( opSpecs.map ( opSpec => OperationContext( uid, identifier, data_list.map(_.uid), opSpec ) ) map ( op => op.identifier -> op ) :_* )
  val operation_list: Seq[OperationContext] = operation_map.values.toSeq
  val variableMap: Map[String, DataContainer] = TaskRequest.buildVarMap(data_list, operation_list)
  val domainMap: Map[String, DomainContainer] = TaskRequest.buildDomainMap(domain_list)
  TaskRequest.inferDomains(operation_list, variableMap )
  val sources = data_list.flatMap( input => input.getSourceOpt )
  val inputs: Seq[(String,String,String)] = sources.map( source => ( source.collection.id, source.name, source.declared_domain.getOrElse( throw new Exception( s"No domain declared for data source ${source.name}" ) ) ) )
  val inputSizes = inputs.map { case ( colId, varId, domId ) => {
    val domain: DomainContainer = domainMap.getOrElse( domId, throw new Exception(s" %JS Can't find domain $domId in job, known domains = [ ${domainMap.keys.mkString(", ")} ] "))
    val collectionNode: xml.Node = findCollectionNode( colId, collectionsNode ).getOrElse(throw new Exception(s" %JS Can't find collection $colId in Job, cols(${getNumCollections(collectionsNode)}) = [${getChildIds(collectionsNode).mkString(",")}], parent = \n${Util.sample(collectionsNode)}"))
    val variableNode: xml.Node = findVariableNode( varId, collectionNode ).getOrElse(throw new Exception(s" %JS Can't find variable $varId in Collection $colId, childIds = [${getChildIds(collectionsNode).mkString(",")}]"))
    val dims = getNodeAttribute( variableNode,"dims").getOrElse(throw new Exception(s" %JS Can't find dims attr in variable '$varId' node in Collection $colId"))
    val shape = getNodeAttribute( variableNode,"shape").getOrElse(throw new Exception(s" %JS Can't find shape attr in variable '$varId' node in Collection $colId" ))
    val resolutions = new CollectionResolution(getNodeAttribute(variableNode, "resolution").getOrElse(throw new Exception(s" %JS Can't find resolution for collection ${colId}")))
    val sizes: Seq[Int] = for( axis <- domain.axes; resolution = resolutions.getResolution(axis.getCFAxisName).getOrElse(throw new Exception(s" %JS Can't find resolution for axis ${axis.getCFAxisName} in collection ${domId}")  ) ) yield {
      axis.system match {
        case "indices" => axis.end.toInt - axis.start.toInt
        case "values" => ( (axis.end.toDouble - axis.start.toDouble ) / resolution ).toInt
        case "timestamps" =>
          val t0 = new DateTime(axis.start.toString).getMillis
          val t1 = new DateTime(axis.end.toString).getMillis
          ( ( t1 - t0 ) / resolution ).toInt
      }
    }
    sizes.product
  }}


  def findCollectionNode( colId: String, collectionsNode: xml.Node ): Option[xml.Node] = {
    collectionsNode.child.find(node => getNodeAttribute(node,"id").fold(false)(_.equalsIgnoreCase(colId)))
  }

  def getChildIds(parentNode: xml.Node ): Seq[String] = parentNode.child.flatMap( node => getNodeAttribute(node,"id") )

  def getNumCollections(collectionsNode: xml.Node ): Int = collectionsNode.child.length

  def findVariableNode( varId: String, collectionNode: xml.Node ): Option[xml.Node] = {
    for( agg_node <- collectionNode.nonEmptyChildren ) {
      val optVarNode = agg_node.child.find(getNodeAttribute(_,"name").fold(false)(_.equalsIgnoreCase(varId)))
      if( optVarNode.isDefined) { return optVarNode }
    }
    None
  }

  def getNodeAttribute( node: xml.Node, attrId: String ): Option[String] = {
    node.attribute( attrId ).flatMap( _.find( _.nonEmpty ).map( _.text ) )
  }
}


object StatusValue extends Enumeration { val QUEUED, EXECUTING, COMPLETED, ERROR, UNDEFINED = Value }
case class WPSJobStatus( job: Job, queue: String ) {
  private var _report: String = ""
  private var _status: StatusValue.Value = StatusValue.QUEUED
  private var _queue: String = queue
  private var _startNTime = System.nanoTime()
  def timeInStatus: Int = ((System.nanoTime()-_startNTime)/1.0e9).toInt
  def timeInJob: Int = job.elapsed
  private def resetTimer(): Unit = { _startNTime = System.nanoTime() }
  def isExecuting: Boolean = (getStatus == StatusValue.EXECUTING)
  def toXml: xml.Node = <job id={job.identifier} rId={job.requestId} queue={_queue} status={_status.toString} age={timeInJob.toString} timeInStatus={timeInStatus.toString}> {_report} </job>
  private var _params = mutable.HashMap.empty[String,String]

  def updateStatus( status: StatusValue.Value, report: String, queue: String, elapsed: Int ) : Unit =  {
    _status = status
    _report = report
    _queue = queue
    if(_status != status) { resetTimer() }
  }

  def config( key: String, value: String ): Unit = _params += (( key, value ))
  def +=( param: (String,String) ): Unit = _params += param
  def params: Iterator[(String,String)] = _params.iterator
  def param( key: String ): Option[String] = _params.get( key )

  def setStatus(status: StatusValue.Value): Unit = if(_status != status) {
    _status = status
    resetTimer()
  }

  def getStatus: StatusValue.Value = {
    _status
  }

  def setReport(report: String): Unit = {
    _report = report
  }

  def getReport: String = {
    _report
  }

  def getQueue: String = {
    _queue
  }
}

class WPS @Inject() (lifecycle: ApplicationLifecycle) extends Controller with Loggable {
  val play_app = current;
  EDASLogManager.isMaster
  val printer = new scala.xml.PrettyPrinter(200, 3)
  logger.info( "\n ------------------------- EDASW: Application STARTUP ----------------------------------- \n" )
  val serverRequestManager = new ServerRequestManager()
  lazy val collections: xml.Node = serverRequestManager.getCapabilities("col")
  serverRequestManager.initialize()
  lifecycle.addStopHook( { () => term() } )

  def term(): Future[Unit] = {
    try {
      logger.info("\n ------------------------- EDASW: Application SHUTDOWN ----------------------------------- \n")
      serverRequestManager.term()
      logger.close()
      Future.successful[Unit](Unit)
    } catch { case err: Exception => Future.failed(err) }
  }

  def executeUtilityRequest( request: String ): Node = {
    if( request.equalsIgnoreCase( "reset" ) ) {
      serverRequestManager.resetJobQueues
      createResponse("util.reset")
    } else {
      throw new Exception( "Unknown utility request: " + request )
    }
  }

  def execute(request: String, identifier: String, datainputs: String, responseForm: String, status: String, storeExecuteResponse: String) = Action {
    try {
      request.toLowerCase match {
        case "getcapabilities" =>
          logger.info(s"getcapabilities: identifier = ${identifier}")
          if( identifier.startsWith("col") ) { Ok( collections ).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*") }
          else { Ok(serverRequestManager.getCapabilities(identifier)).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*") }
        case "describeprocess" =>
          Ok( serverRequestManager.describeProcess( identifier) ).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
        case "execute" =>
          val idToks = identifier.split('.')
          if( idToks(0).equalsIgnoreCase( "util" ) ) {
            Ok( executeUtilityRequest( idToks.last ) ).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
          } else {
            val runargs = Map("storeExecuteResponse" -> storeExecuteResponse.toLowerCase, "status" -> status.toLowerCase, "responseform" -> responseForm )
            val jobId: String = runargs.getOrElse("jobId", RandomStringUtils.random(8, true, true))
            logger.info(s"Received WPS Request: Creating Job, jobId=${jobId}, identifier=${identifier}, runargs={${runargs.mkString(";")}}, datainputs=${datainputs}")
            val job = new WPSJob(jobId, identifier, datainputs, runargs, collections, 1.0f)
            serverRequestManager.addJob(job)
            val result = createResponse(jobId)
            //         BadRequest(response).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
            Ok(result).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
          }
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

//  def getResultFile(id: String, service: String) = Action {
//    try {
//      serverRequestManager.getResultFilePath(service, id) match {
//        case Some(resultFilePath: String) =>
//          logger.info(s"WPS getResult: resultFilePath=$resultFilePath")
//          Ok.sendFile(new File(resultFilePath)).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
//        case None =>
//          NotFound("Result not yet available").withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
//      }
//    } catch {
//      case e: Exception =>
//        InternalServerError(e.getMessage)
//          .withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
//    }
//  }

  def getResult(id: String, service: String) = Action {
    try {
      val result = serverRequestManager.getResult(service, id, ResponseSyntax.WPS )
      Ok(result).withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    } catch {
      case e: Exception =>  InternalServerError(e.getMessage) .withHeaders(ACCESS_CONTROL_ALLOW_ORIGIN -> "*")
    }
  }

}

object JobQueues {
  def create(config: Map[String, String] ): Seq[JobQueue] = {
    val thresholds = config.getOrElse( "job.queue.thresholds", "FastLane:250m,SlowLane:5g" )
    thresholds.split( "," ).map( qSpec => JobQueues.factory(qSpec) ).sortBy( _.threshold )
  }

  def factory( queueSpec: String ): JobQueue = {
    val queueSpecItems = queueSpec.split( ":" )
    assert( queueSpecItems.length == 2, s"Format error in Job Queue Spec, should be something like 'SlowLane:5g', actual value = $queueSpecItems" )
    JobQueue( queueSpecItems(0), cdsutils.parseMemsize(queueSpecItems(1)) )
  }
}

case class JobQueue( name: String, threshold: Long ) {
  private val _queue = new PriorityBlockingQueue[String]()
  private var _currentJob: Option[String] = None
  def +=( jobId: String ): Unit = _queue.add( jobId )
  def size = _queue.size
  def popJob( waitTimeSecs: Int ): Option[String] = { _currentJob = Option( _queue.poll(waitTimeSecs,TimeUnit.SECONDS) ); _currentJob }
  def currentJob: Option[String] = _currentJob
  def clear: Unit = { _currentJob = None; _queue.clear() }
}

case class CollectionResolution( spec: String ) {
  private val _resolution: Map[String,Float] = Map( spec.split(',').toSeq.map( _.split(':').toSeq ).map( dim => dim(0).toLowerCase -> dim(1).toFloat): _* )
  def getResolution( dim: String ): Option[Float] = _resolution.get( dim.toLowerCase )

}

class ServerRequestManager extends Thread with Loggable {
  val config: Map[String, String] = serverConfiguration
  val jobQueues: Seq[JobQueue] = JobQueues.create(config)
  val jobDirectory = TrieMap.empty[String,WPSJobStatus]
  private var _active = true;
  val processesCache = TrieMap.empty[String,xml.Node]
  val responseCache = TrieMap.empty[String,xml.Node]
  var active: Boolean = true;

  appParameters.setCustomCacheDir( config.getOrElse( "edas.cache.dir", "" ) )
  appParameters.addConfigParams(config)
  val server_address = config.getOrElse( "edas.server.address", "" )
  val response_syntax: ResponseSyntax.Value = ResponseSyntax.fromString( config.getOrElse( "edas.response.syntax", "wps" ) )
  protected var processManager: Option[GenericProcessManager] = None

  def findQueue( jobSize: Long ): Option[JobQueue] = jobQueues.find( jobSize < _.threshold )
  def maxJobSize: Long = jobQueues.last.threshold

  def term() = {
    active = false;
    processManager.map( _.term() )
  }

//  def getResultFilePath( service: String, resultId: String ): Option[String] = processManager match {
//    case Some( procMgr ) => procMgr.getResultFilePath( service, resultId )
//    case None => throw new Exception( "Attempt to access undefined ProcessManager")
//  }

  def getResult( service: String, resultId: String, response_syntax: ResponseSyntax.Value ): xml.Node= processManager match {
    case Some( procMgr ) => procMgr.getResult( service, resultId, response_syntax )
    case None => throw new Exception( "Attempt to access undefined ProcessManager")
  }
  def getResultStatus( service: String, resultId: String, response_syntax: ResponseSyntax.Value ): xml.Node= processManager match {
    case Some( procMgr ) => procMgr.getResultStatus( service, resultId, response_syntax )
    case None => throw new Exception( "Attempt to access undefined ProcessManager")
  }

  def deactivate(): Unit = { active = false; }

  def addJob( job: WPSJob ): Unit = {
    val jobSize = getJobSize(job)
    findQueue( jobSize ) match {
      case Some(queue) =>
        jobDirectory += ( job.requestId -> WPSJobStatus( job, queue.name ) )
        queue += job.requestId
        logger.info( s"EDASW:Added job ${job.requestId} to job queue, nJobs = ${queue.size}"  )
      case None =>
        throw new Exception( s"Job request is too large: $jobSize, max job size = $maxJobSize" )
    }
  }

  def resetJobQueues( ): Unit = {
    jobDirectory.clear()
    jobQueues.foreach( _.clear )
  }

  def getJobSize( job: WPSJob ): Long = job.inputSizes.sum

  def initialize(): Unit = {
    setDaemon(true)
    start()
    //    getCapabilities("")
    //    getCapabilities("col")
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
          logger.info( s"EDASW::getResponse($responseId): ${Util.sample(raw_message)}" )
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
    val pattern = "[$][{][A-Z0-9a-z._]+[}]".r
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

  def executeQuery( job: Job, timeout_sec: Int = 180 ): xml.Node = {
    val jobQueue = jobQueues.head
    jobDirectory += ( job.requestId -> WPSJobStatus(job,jobQueue.name) )
    logger.info( "EDASW::executeQuery: " + job.requestId  )
    jobQueue += job.requestId
    getResponse( job.requestId, timeout_sec )
  }

  def jobExecuting( jobId: String ): Boolean = jobDirectory.get(jobId).exists( _.isExecuting )
  def jobsExecuting: Boolean = jobDirectory.values.exists( _.isExecuting )
  def waitUntilAllJobsComplete: Unit = { while ( active && jobsExecuting ) { Thread.sleep( 100 ) } }
  def waitUntilJobCompletes(jobId: String): Unit = { while ( active && jobExecuting(jobId) ) { Thread.sleep( 100 ) } }

  def updateJobStatus( requestId: String, status: StatusValue.Value, report: String  ): Job = {
    logger.info( "EDASW::updateJobStatus: " + requestId + ", status = " + status.toString )
    jobDirectory.get( requestId ) match {
      case Some( jobStatus ) =>
        jobStatus.setStatus( status )
        jobStatus.setReport( report )
        jobStatus.job
      case None => throw new Exception( "Attempt[1] to set status on non-existent job: " + requestId + ", jobs = " + jobDirectory.keys.mkString(", ") )
    }
  }


  def getCapabilities( identifier: String ): xml.Node =
    if( identifier.toLowerCase.startsWith("job") ) { getJobReport }
    else {  executeQuery( new Job( "getcapabilities:" + identifier, identifier, 1.0f ) ) }

  def describeProcess( identifier: String ): xml.Node = {
    processesCache.getOrElseUpdate( identifier, executeQuery( new Job( "describeprocess:" + identifier, identifier, 1.0f ) ) )
  }

  def getJobReport: xml.Node = {
    <jobs>  { jobDirectory.values.map( _.toXml ) } </jobs>
  }


  def getJobStatus( requestId: String ): xml.Elem = {
    val status: WPSResponse = jobDirectory.get( requestId ) match {
      case Some( jobStatus ) =>
        jobStatus.getStatus match {
          case StatusValue.EXECUTING => new WPSExecuteStatusStarted( "WPS", jobStatus.getReport, requestId, jobStatus.timeInStatus )
          case StatusValue.COMPLETED => new WPSExecuteStatusCompleted( "WPS", jobStatus.getReport, requestId )
          case StatusValue.ERROR =>     new WPSExecuteStatusError( "WPS", jobStatus.getReport, requestId )
          case StatusValue.QUEUED =>
            if( processManager.fold( true )( _.serverIsDown ) ) {
              jobDirectory.remove( requestId )
              new WPSExecuteStatusError( "WPS", "EDAS Analytics Server is Currently Down", requestId )
            } else {
              new WPSExecuteStatusQueued("WPS", jobStatus.getReport, requestId, jobStatus.getQueue, jobStatus.timeInStatus)
            }
        }
      case None =>
        val msg = s"Job ${requestId} has been killed by system administration"
        new WPSExecuteStatusError( "WPS", "NonExistentJob: " + msg, requestId )
    }
    status.toXml( response_syntax )
  }

  override def run() {
    logger.info( "EDASW: Starting webProcessManager with server_address = " + server_address + ", EDAS libs logging to: " + EDASLogManager.getCurrentLogger.logFilePath.toString )
    processManager = Some( if( server_address.isEmpty ) { new ProcessManager(config) } else { new zmqProcessManager(config) } )
    try {
      while ( active ) {
        jobQueues.foreach( jobQueue => if( ! jobQueue.currentJob.fold(false)(jobExecuting) ) {
//          logger.info("EDASW::Polling job queue: " + jobQueue.toString)
          jobQueue.popJob( 1 ) match {
            case Some(jobId) =>
              logger.info("EDASW::Popped job for exec: " + jobId)
              val result = submitJob(processManager.get, jobId)
            case None =>
              // logger.info(s"EDASW:: Looking for jobs in queue, nJobs = ${jobQueue.size}")
          }
        })
      }
    } catch {
      case exc: InterruptedException => return
    }
  }

  def submitJob( processMgr: GenericProcessManager, jobId: String ): xml.Node = try {
    val t0 = System.nanoTime()
    val job = updateJobStatus( jobId, StatusValue.EXECUTING, "" )
    job.requestId.toLowerCase match {
      case jobId if jobId.startsWith("getcapabilities") =>
        jobCompleted( jobId, processMgr.getCapabilities( "cds2", job.identifier, job.runargs ), true )
      case jobId if jobId.startsWith("describeprocess") =>
        jobCompleted( jobId, processMgr.describeProcess( "cds2", job.identifier, job.runargs ), true )
      case _ =>
        logger.info (s"\n\nEDASW::Popped job identifier=${job.identifier}, datainputs=${job.datainputs}\n\n")
        val parsed_data_inputs = wpsObjectParser.parseDataInputs (job.datainputs)
        logger.info (s"EDASW::Executing Process, job identifier=${job.identifier}, job requestId=${job.requestId}, jobId=${jobId}")
        val (responseId, responseElem ) = processMgr.executeProcess( "cds2", job )
        processMgr.waitUntilJobCompletes( "cds2", responseId )
        jobCompleted(jobId, responseElem, true )
        logger.info ("EDASW::Completed request '%s' in %.4f sec".format (job.identifier, (System.nanoTime () - t0) / 1.0E9) )
        responseElem
    }
  } catch {
    case ex: Exception =>
      val exception = WPS_XML.extractErrorMessage(ex)
      val response_xml = new WPSExceptionReport( exception ).toXml( response_syntax )
      val msg = s"\nJob exited with error --> jobId=$jobId, response=${response_xml.toString}\n"
      logger.info (msg)
      print(msg)
      jobCompleted( jobId, response_xml, false )
      response_xml
  }

  def jobCompleted( jobId: String, results: xml.Node, success: Boolean ): xml.Node  = {
    responseCache += ( jobId -> results )
    val completion_status = if(success) { StatusValue.COMPLETED } else { StatusValue.ERROR }
    updateJobStatus ( jobId, completion_status, results.toString )
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
