package process

import scala.collection.mutable
import scala.collection.immutable
import scala.xml._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import process.exceptions._
import servers.APIManager

class ProcessInput(val name: String, val itype: String, val maxoccurs: Int, val minoccurs: Int) {

  def toXml = {
    <input id={ name } type={ itype } maxoccurs={ maxoccurs.toString } minoccurs={ minoccurs.toString }/>
  }
}

class Process(val name: String, val description: String, val inputs: List[ProcessInput]) {

  def toXml =
    <process id={ name }>
      <description id={ description }> </description>
      <inputs>
        { inputs.map(_.toXml ) }
      </inputs>
    </process>

  def toXmlHeader =
    <process id={ name }> <description> { description } </description> </process>
}

class ProcessList(val process_list: List[Process]) {

  def toXml =
    <processes>
      { process_list.map(_.toXml ) }
    </processes>

  def toXmlHeaders =
    <processes>
      { process_list.map(_.toXmlHeader ) }
    </processes>
}

class ProcessManager(process_list: List[Process]) {
  private val processMap: Map[String, Process] = Map[String, Process](process_list.map( p => p.name.toLowerCase -> p ): _*)
  val logger = LoggerFactory.getLogger(classOf[ProcessManager])
  def apiManager = APIManager()

  def printLoggerInfo = {
    import ch.qos.logback.classic.LoggerContext
    import ch.qos.logback.core.util.StatusPrinter
    StatusPrinter.print( LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext] )
  }

  def unacceptable(msg: String): Unit = {
    logger.error(msg)
    throw new NotAcceptableException(msg)
  }

  def describeProcess(name: String) = processMap.get(name.toLowerCase) match {
    case Some(p) => Some(p.toXml)
    case None => None
  }

  def listProcesses = <processes> { processMap.values.map(_.toXmlHeader) } </processes>

  def executeProcess(service: String, process_name: String, datainputs: Map[String, Seq[Map[String, Any]]], runargs: Map[String, Any]): xml.Elem = {
    processMap.get(process_name.toLowerCase) match {
      case Some(p) =>
        apiManager.getServiceProvider(service) match {
          case Some(serviceProvider) =>
            serviceProvider.executeProcess(process_name, datainputs, runargs)
          case None =>
            throw new NotAcceptableException("Unrecognized service: " + service)
        }
      case None => throw new NotAcceptableException("Unrecognized process: " + process_name)
    }
  }
}

object webProcessManager extends ProcessManager(
  List(
    new Process("CWT.Sum", "SpatioTemporal Sum of Inputs", List(new ProcessInput("Var1", "Float", 1, 1), new ProcessInput("Var2", "Float", 1, 1))),
    new Process("CWT.Average", "SpatioTemporal Ave of Inputs", List(new ProcessInput("Var1", "Float", 1, 1), new ProcessInput("Var2", "Float", 1, 1)))
  )
)

object testProcessManager extends App {
  println( webProcessManager.listProcesses )
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

