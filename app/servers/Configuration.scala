package servers

import java.io.{PrintWriter, StringWriter}
import java.util.concurrent.ExecutionException
import nasa.nccs.utilities.cdsutils
import org.slf4j.LoggerFactory
import utilities.parsers.wpsObjectParser.cdata

abstract class ServiceProvider {
  val logger = LoggerFactory.getLogger(this.getClass)

  def executeProcess(identifier: String, parsed_data_inputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, String]): xml.Elem

  def listProcesses(): xml.Elem

  def describeProcess( identifier: String ): xml.Elem

  def getCause( e: Exception ): Throwable = e match {
    case err: ExecutionException => err.getCause; case x => e
  }

  def fatal( e: Exception ): xml.Elem = {
    val err = getCause( e )
    logger.error( "\nError Executing Kernel: %s\n".format(err.getMessage) )
    val sw = new StringWriter
    err.printStackTrace(new PrintWriter(sw))
    logger.error( sw.toString )
    <error id="Execution Error"> { err.getMessage } </error>
  }

}

object esgfServiceProvider extends ServiceProvider {
  import nasa.nccs.esgf.engine.demoExecutionManager

  override def executeProcess(process_name: String, datainputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, String]): xml.Elem = {
    try { demoExecutionManager.execute(process_name, datainputs, runargs) } catch { case e: Exception => <error id="Execution Error"> {e.getMessage} </error> }
  }
  override def describeProcess(process_name: String): xml.Elem = {
    try {  demoExecutionManager.describeProcess(process_name) } catch {  case e: Exception => <error id="Execution Error"> {e.getMessage} </error> }
  }
  override def listProcesses(): xml.Elem = {
    try {  demoExecutionManager.listProcesses() } catch {  case e: Exception => <error id="Execution Error"> {e.getMessage} </error> }
  }
}

object cds2ServiceProvider extends ServiceProvider {
  import nasa.nccs.cds2.engine.CDS2ExecutionManager
  import nasa.nccs.esgf.process.TaskRequest

  val cds2ExecutionManager = new CDS2ExecutionManager()

  override def executeProcess(process_name: String, datainputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, String]): xml.Elem = {
    try {
      cdsutils.time( logger, "-->> Process %s: Total Execution Time: ".format(process_name) ) {
        cds2ExecutionManager.execute(TaskRequest(process_name, datainputs), runargs)
      }
    } catch { case e: Exception => fatal(e) }
  }
  override def describeProcess(process_name: String): xml.Elem = {
    try {
      cds2ExecutionManager.describeProcess( process_name )

    } catch { case e: Exception => fatal(e) }
  }
  override def listProcesses(): xml.Elem = {
    try {
      cds2ExecutionManager.listProcesses()

    } catch { case e: Exception => fatal(e) }
  }
}

object demoServiceProvider extends ServiceProvider {

  override def executeProcess(identifier: String, parsed_data_inputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, String]): xml.Elem = {
    <result id={ identifier }>
      <inputs>  { cdata(parsed_data_inputs) } </inputs>
      <runargs> { cdata(runargs) } </runargs>
    </result>
  }
  override def describeProcess(identifier: String): xml.Elem = {
    <process id={ identifier }></process>
  }
  override def listProcesses(): xml.Elem = {
    <processes></processes>
  }

}

object ServiceProviderConfiguration {

  val providers = Seq(
    "esgf" -> esgfServiceProvider,
    "cds2" -> cds2ServiceProvider,
    "demo" -> demoServiceProvider,
    "test" -> esgfServiceProvider
  )

  val default_service = "esgf"

}
