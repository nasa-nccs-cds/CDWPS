package nccs.process

import scala.util.matching.Regex
import scala.collection.mutable
import scala.collection.immutable
import scala.xml._
import mutable.ListBuffer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import nccs.process.exceptions._
import nccs.utilities.numbers.GenericNumber

case class ErrorReport(severity: String, message: String) {
  override def toString() = {
    s"ErrorReport { severity: $severity, message: $message }"
  }

  def toXml() = {
      <error severity={severity} message={message}/>
  }
}

class TaskRequest(val name: String,  val workflows: List[WorkflowContainer], val variableMap : Map[String,Any], val domainMap: Map[String,DomainContainer] ) {
  val errorReports = new ListBuffer[ErrorReport]()
  val logger = LoggerFactory.getLogger( classOf[TaskRequest] )
  validate()

  def addErrorReport(severity: String, message: String) = {
    val error_rep = ErrorReport(severity, message)
    logger.error(error_rep.toString)
    errorReports += error_rep
  }

  def validate(): Unit = {
    for( variable <- inputVariables; domid = variable.domain; vid=variable.id; if !domid.isEmpty ) {
      if ( !domainMap.contains(domid) ) {
        var keylist = domainMap.keys.mkString("[",",","]")
        logger.error( s"Error, No $domid in $keylist in variable $vid" )
        throw new NotAcceptableException( s"Error, Missing domain $domid in variable $vid" )
      }
    }
    for (workflow <- workflows; operation <- workflow.operations; opid = operation.id; var_arg <- operation.inputs) {
      if (!variableMap.contains(var_arg)) {
        var keylist = variableMap.keys.mkString("[", ",", "]")
        logger.error(s"Error, No $var_arg in $keylist in operation $opid")
        throw new NotAcceptableException(s"Error, Missing variable $var_arg in operation $opid")
      }
    }
  }

  override def toString = {
    var taskStr = s"TaskRequest { name='$name', variables = '$variableMap', domains='$domainMap', workflows='$workflows' }"
    if (!errorReports.isEmpty) {
      taskStr += errorReports.mkString("\nError Reports: {\n\t", "\n\t", "\n}")
    }
    taskStr
  }

  def toXml() = {
    <task_request name={name}>
      <data>
        { inputVariables.map(_.toXml()) }
      </data>
      <domains>
        {domainMap.values.map(_.toXml())}
      </domains>
      <operation>
        { workflows.map(_.toXml()) }
      </operation>
      <error_reports>
        {errorReports.map(_.toXml())}
      </error_reports>
    </task_request>
  }

  def inputVariables(): Traversable[DataContainer] = {
    for( variableSource <- variableMap.values; if variableSource.isInstanceOf[ DataContainer ] ) yield variableSource.asInstanceOf[DataContainer]
  }
}

object TaskRequest {
  def apply(process_name: String, datainputs: Map[String, Seq[Map[String, Any]]]) = {
    val data_list = datainputs.getOrElse("variable", List()).map(DataContainer(_)).toList
    val domain_list = datainputs.getOrElse("domain", List()).map(DomainContainer(_)).toList
    val operation_list = datainputs.getOrElse("operation", List()).map(WorkflowContainer(_)).toList
    val variableMap = buildVarMap( data_list, operation_list )
    val domainMap = buildDomainMap( domain_list )
    new TaskRequest( process_name, operation_list, variableMap, domainMap )
  }

  def buildVarMap( data: List[DataContainer], workflow: List[WorkflowContainer] ): Map[String,Any] = {
    var var_items = new ListBuffer[(String,Any)]()
    for( data_container <- data ) var_items += ( data_container.uid -> data_container )
    for( workflow_container<- workflow; operation<-workflow_container.operations; if !operation.result.isEmpty ) var_items += ( operation.result -> operation )
    var_items.toMap[String,Any]
  }

  def buildDomainMap( domain: List[DomainContainer] ): Map[String,DomainContainer] = {
    var domain_items = new ListBuffer[(String,DomainContainer)]()
    for( domain_container <- domain ) domain_items += ( domain_container.id -> domain_container )
    domain_items.toMap[String,DomainContainer]
  }
}

class ContainerBase {
  val logger = LoggerFactory.getLogger( classOf[ContainerBase] )
  def item_key(map_item: (String, Any)): String = map_item._1

  def normalize(sval: String): String = sval.stripPrefix("\"").stripSuffix("\"").toLowerCase

  def getStringKeyMap( generic_map: Map[_,_] ): Map[String,Any] = {
    assert( generic_map.isEmpty | generic_map.keys.head.isInstanceOf[ String ] )
    generic_map.asInstanceOf[ Map[String,Any] ]
  }

  def key_equals(key_value: String)(map_item: (String, Any)): Boolean = {
    item_key(map_item) == key_value
  }

  def key_equals(key_regex: Regex)(map_item: (String, Any)): Boolean = {
    key_regex.findFirstIn(item_key(map_item)) match {
      case Some(x) => true;
      case None => false;
    }
  }

  //  def key_equals( key_expr: Iterable[Any] )( map_item: (String, Any) ): Boolean = { key_expr.map( key_equals(_)(map_item) ).find( ((x:Boolean) => x) ) }
  def filterMap(raw_metadata: Map[String, Any], key_matcher: (((String, Any)) => Boolean)): Any = {
    raw_metadata.find(key_matcher) match {
      case Some(x) => x._2;
      case None => None
    }
  }

  def toXml() = {
    <container>
      {"<![CDATA[ " + toString + " ]]>"}
    </container>
  }

  def getGenericNumber( opt_val: Option[Any] ): GenericNumber = {
    opt_val match {
      case Some(p) => GenericNumber(p)
      case None =>    GenericNumber()
    }
  }
  def getStringValue( opt_val: Option[Any] ): String = {
    opt_val match {
      case Some(p) => p.toString
      case None => ""
    }
  }
}

object containerTest extends App {
  val c = new ContainerBase()
  val tval = Some( 4.7 )
  val fv = c.getGenericNumber( tval )
  println( fv )
}


class DataContainer(val id: String, val dset: String, val domain: String) extends ContainerBase {
  override def toString = {
    s"DataContainer { id = $id, dset = $dset, domain = $domain }"
  }

  override def toXml() = {
      <dataset id={id} dset={dset.toString} domain={domain.toString}/>
  }
  def uid(): String = id.split(':').last
}

object DataContainer extends ContainerBase {
  def apply(metadata: Map[String, Any]): DataContainer = {
    try {
      val dset = filterMap(metadata, key_equals("dset"))
      val id = filterMap(metadata, key_equals("id"))
      val domain = filterMap(metadata, key_equals("domain"))
      new DataContainer(normalize(id.toString), normalize(dset.toString), normalize(domain.toString) )
    } catch {
      case e: Exception => {
        logger.error("Error creating DataContainer: " + e.getMessage  )
        logger.error( e.getStackTrace.mkString("\n") )
        throw new NotAcceptableException( e.getMessage, e )
      }
    }
  }
}

class DomainContainer( val id: String, val axes: List[DomainAxis] ) extends ContainerBase {
  override def toString = {
    s"DomainContainer { id = $id, axes = $axes }"
  }
  override def toXml() = {
    <domain id={id}>
      <axes> { axes.map( _.toXml ) } </axes>
    </domain>
  }
}

object DomainAxis extends ContainerBase {
  def apply( id: String, axis_spec: Any ): Option[DomainAxis] = {
    axis_spec match {
      case generic_axis_map: Map[_,_] =>
        val axis_map = getStringKeyMap( generic_axis_map )
        val start = getGenericNumber( axis_map.get("start") )
        val end = getGenericNumber( axis_map.get("end") )
        val system = getStringValue( axis_map.get("system") )
        val bounds = getStringValue( axis_map.get("bounds") )
        new Some( new DomainAxis( normalize(id), start, end, system, bounds ) )
      case None => None
      case _ =>
        val msg = "Unrecognized DomainAxis spec: " + axis_spec.getClass.toString
        logger.error( msg )
        throw new Exception(msg)
    }
  }
}

class DomainAxis( val d_id: String, val d_start: GenericNumber, val d_end: GenericNumber, val d_system: String, val d_bounds: String ) extends ContainerBase  {

  override def toString = {
    s"DomainAxis { id = $d_id, start = $d_start, end = $d_end, system = $d_system, bounds = $d_bounds }"
  }

  override def toXml() = {
    <axis id={d_id} start={d_start.toString} end={d_end.toString} system={d_system} bounds={d_bounds} />
  }
}

object DomainContainer extends ContainerBase {
  def apply(metadata: Map[String, Any]): DomainContainer = {
    var items = new ListBuffer[ Option[DomainAxis] ]()
    try {
      val id = filterMap(metadata, key_equals("id"))
      items += DomainAxis("lat", filterMap(metadata, key_equals( """lat*""".r)))
      items += DomainAxis("lon", filterMap(metadata, key_equals( """lon*""".r)))
      items += DomainAxis("lev", filterMap(metadata, key_equals( """lev*""".r)))
      items += DomainAxis("time", filterMap(metadata, key_equals( """tim*""".r)))
      new DomainContainer( normalize(id.toString), items.flatten.toList )
    } catch {
      case e: Exception => {
        logger.error("Error creating DomainContainer: " + e.getMessage )
        logger.error( e.getStackTrace.mkString("\n") )
        throw new NotAcceptableException( e.getMessage, e )
      }
    }
  }
}

class WorkflowContainer(val operations: Iterable[OperationContainer]) extends ContainerBase {
  override def toString = {
    s"WorkflowContainer { operations = $operations }"
  }
  override def toXml() = {
    <workflow>  { operations.map( _.toXml ) }  </workflow>
  }
}

object WorkflowContainer extends ContainerBase {
  def apply(metadata: Map[String, Any]): WorkflowContainer = {
    try {
      import nccs.utilities.wpsOperationParser
      val parsed_data_inputs = wpsOperationParser.parseOp(metadata("unparsed").toString)
      new WorkflowContainer(parsed_data_inputs.map(OperationContainer(_)))
    } catch {
      case e: Exception => {
        val msg = "Error creating WorkflowContainer: " + e.getMessage
        logger.error(msg)
        throw new NotAcceptableException(msg)
      }
    }
  }
}

class OperationContainer(val id: String, val name: String, val result: String = "", val inputs: List[String], val optargs: Map[String,String])  extends ContainerBase {
  override def toString = {
    s"OperationContainer { id = $id,  name = $name, result = $result, inputs = $inputs, optargs = $optargs }"
  }
  override def toXml() = {
    <proc id={id} name={name} result={result} inputs={inputs.toString} optargs={optargs.toString}/>
  }
}

object OperationContainer extends ContainerBase {
  def apply(raw_metadata: Any): OperationContainer = {
    raw_metadata match {
      case (id: String, args: List[_]) => {
        val varlist = new ListBuffer[String]()
        val optargs = new ListBuffer[(String,String)]()
        for( raw_arg<-args; arg=raw_arg.toString ) {
          if(arg contains ":") {
            val arg_items = arg.split(":")
            optargs += ( arg_items(0) -> arg_items(1) )
          }
          else varlist += arg
        }
        val ids = id.split("~")
        ids.length match {
          case 1 => new OperationContainer( id = id, name="$PROCESSNAME", result = ids(0), inputs = varlist.toList, optargs=optargs.toMap[String,String] )
          case 2 => new OperationContainer( id = id, name = ids(0), result = ids(1), inputs = varlist.toList, optargs=optargs.toMap[String,String] )
          case _ => {
            val msg = "Unrecognized format for Operation id: " + id
            logger.error(msg)
            throw new Exception(msg)
          }
        }
      }
      case _ => {
        val msg = "Unrecognized format for OperationContainer: " + raw_metadata.toString
        logger.error(msg)
        throw new Exception(msg)
      }
    }
  }
}


class TaskProcessor {

}
