package servers

import utilities.parsers.wpsObjectParser.cdata

abstract class ServiceProvider {

  def executeProcess(identifier: String, parsed_data_inputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, Any]): xml.Elem

}

object esgfServiceProvider extends ServiceProvider {

  override def executeProcess(process_name: String, datainputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, Any]): xml.Elem = {
    import nasa.nccs.esgf.engine.demoExecutionManager
    try {

      demoExecutionManager.execute(process_name, datainputs, runargs)

    } catch {
      case e: Exception => <error id="Execution Error"> {e.getMessage} </error>
    }
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
}

object demoServiceProvider extends ServiceProvider {

  override def executeProcess(identifier: String, parsed_data_inputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, Any]): xml.Elem = {
    <result id={ identifier }>
      <inputs>  { cdata(parsed_data_inputs) } </inputs>
      <runargs> { cdata(runargs) } </runargs>
    </result>
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
