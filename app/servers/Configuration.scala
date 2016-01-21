package servers
import utilities.parsers.wpsObjectParser.cdata

abstract class ServiceProvider {

  def executeProcess(identifier: String, parsed_data_inputs: Map[String, Any], runargs: Map[String, Any]): xml.Elem

}

object esgfServiceProvider extends ServiceProvider {

  override def executeProcess(identifier: String, parsed_data_inputs: Map[String, Any], runargs: Map[String, Any]): xml.Elem = {
    //    import nccs.process.TaskRequest
    //    import nccs.engine.ExecutionManager
    //    try {
    //      val tr = TaskRequest(process_name, datainputs)
    //      ExecutionManager.execute(tr, runargs)
    //      Some(tr)
    //    } catch {
    //      case e: Exception => {
    //        unacceptable(e.getMessage);
    //        None
    //      }
    //    }
    <result id={ identifier }/>
  }
}

object demoServiceProvider extends ServiceProvider {

  override def executeProcess(identifier: String, parsed_data_inputs: Map[String, Any], runargs: Map[String, Any]): xml.Elem = {
    <result id={ identifier }>
      <inputs> { cdata(parsed_data_inputs) } </inputs>
      <runargs> { cdata(runargs) } </runargs>
    </result>
  }

}

object ServiceProviderConfiguration {

  val providers = Seq(
    ("esgf" -> esgfServiceProvider),
    ("demo" -> demoServiceProvider)
  )

  val default_service = "demo"

}
