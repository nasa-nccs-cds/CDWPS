package servers

import utilities.parsers.wpsObjectParser.cdata

abstract class ServiceProvider {

  def executeProcess(identifier: String, parsed_data_inputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, Any]): xml.Elem

  def listProcesses(): xml.Elem

  def describeProcess( identifier: String ): xml.Elem

}

object esgfServiceProvider extends ServiceProvider {
  import nasa.nccs.esgf.engine.demoExecutionManager

  override def executeProcess(process_name: String, datainputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, Any]): xml.Elem = {
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

  override def executeProcess(process_name: String, datainputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, Any]): xml.Elem = {
    try {
      cds2ExecutionManager.execute( TaskRequest( process_name, datainputs ), runargs )
    } catch {
      case e: Exception => <error id="Execution Error"> {e.getMessage} </error>
    }
  }
  override def describeProcess(process_name: String): xml.Elem = {
    try {
      cds2ExecutionManager.describeProcess( process_name )
    } catch {
      case e: Exception => <error id="Execution Error"> {e.getMessage} </error>
    }
  }
  override def listProcesses(): xml.Elem = {
    try {
      cds2ExecutionManager.listProcesses()
    } catch {
      case e: Exception => <error id="Execution Error"> {e.getMessage} </error>
    }
  }
}

object demoServiceProvider extends ServiceProvider {

  override def executeProcess(identifier: String, parsed_data_inputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, Any]): xml.Elem = {
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
