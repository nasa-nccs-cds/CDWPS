package nccs.process
import nccs.engine.ExecutionManager
import scala.collection.mutable
import scala.collection.immutable
import scala.xml._

class ProcessInput(val name: String, val itype: String, val maxoccurs: Int, val minoccurs: Int) {

  def toXml() = {
    <input id={ name } type={ itype } maxoccurs={ maxoccurs.toString() } minoccurs={ minoccurs.toString() }/>
  }
}

class Process(val name: String, val description: String, val inputs: List[ProcessInput]) {

  def toXml() =
    <process id={ name }>
      <description id={ description }> </description>
      <inputs>
        { inputs.map(_.toXml()) }
      </inputs>
    </process>

  def toXmlHeader() =
    <process id={ name }> <description> { description } </description> </process>
}

class ProcessList(val process_list: List[Process]) {

  def toXml() =
    <processes>
      { process_list.map(_.toXml()) }
    </processes>

  def toXmlHeaders() =
    <processes>
      { process_list.map(_.toXmlHeader()) }
    </processes>
}

class ProcessManager(process_list: List[Process]) {
  private val processMap: Map[String, Process] = Map[String, Process](process_list.map(p => (p.name.toLowerCase -> p)): _*)

  def describeProcess(name: String) = processMap.get(name.toLowerCase) match {
    case Some(p) => Some(p.toXml)
    case None => None
  }

  def listProcesses() = <processes> { processMap.values.map(_.toXmlHeader) } </processes>

  def executeProcess(process_name: String, datainputs: Map[String, Seq[ Map[String, Any]] ], runargs: Map[String, Any]) = {
    import nccs.process.TaskRequest
    import nccs.engine.ExecutionManager
    processMap.get(process_name.toLowerCase) match {
      case Some(p) =>
        val tr = TaskRequest( process_name, datainputs )
        if( validateProcesses( tr ) ) {
          ExecutionManager.execute( tr, runargs )
        }
        Some(tr)
      case None =>
        None
    }
  }

  def validateProcesses( tr: TaskRequest ): Boolean = {
    true
  }
}

object webProcessManager extends ProcessManager(
  List(
    new Process("CWT.Sum", "SpatioTemporal Sum of Inputs", List(new ProcessInput("Var1", "Float", 1, 1), new ProcessInput("Var2", "Float", 1, 1))),
    new Process("CWT.Average", "SpatioTemporal Ave of Inputs", List(new ProcessInput("Var1", "Float", 1, 1), new ProcessInput("Var2", "Float", 1, 1)))
  )
)

object testProcessManager extends App {
  println(webProcessManager.listProcesses())
  val process = webProcessManager.describeProcess("CWT.Sum")
  process match {
    case Some(p) => println(p)
    case None => println("Unrecognized process")
  }
}

/*
import org.scalatest.FunSuite

class ParserTest extends FunSuite {

  test("DescribeProcess") {
    println(webProcessManager.listProcesses())
    val process = webProcessManager.describeProcess("CWT.Sum")
    process match {
      case Some(p) => println(p)
      case None => println("Unrecognized process")
    }
    assert(true)
  }

}
*/

