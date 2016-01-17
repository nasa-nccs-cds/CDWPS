package nccs.process

import play.api.Logger
import scala.util.matching.Regex
import scala.collection.mutable
import scala.collection.immutable
import scala.xml._
import mutable.ListBuffer

case class ErrorReport(severity: String, message: String) {
  override def toString() = {
    s"ErrorReport { severity: $severity, message: $message }"
  }

  def toXml() = {
      <error severity={severity} message={message}/>
  }
}

class TaskRequest(val name: String, val data: List[DataContainer], domain: List[DomainContainer], val operation: List[WorkflowContainer]) {
  val errorReports = new ListBuffer[ErrorReport]()
  val logger: Logger = Logger(this.getClass())

  def addErrorReport(severity: String, message: String) = {
    val error_rep = ErrorReport(severity, message)
    Logger.info(error_rep.toString)
    errorReports += error_rep
  }

  override def toString = {
    var taskStr = s"TaskRequest { data = '$data', domain='$domain', operation='$operation' }"
    if (!errorReports.isEmpty) {
      taskStr += errorReports.mkString("\nError Reports: {\n\t", "\n\t", "\n}")
    }
    taskStr
  }

  def toXml() = {
    <task_request name={name}>
      <data>
        {data.map(_.toXml())}
      </data>
      <domain>
        {domain.map(_.toXml())}
      </domain>
      <operation>
        {operation.map(_.toXml())}
      </operation>
      <error_reports>
        {errorReports.map(_.toXml())}
      </error_reports>
    </task_request>
  }
}

object TaskRequest {
  def apply(process_name: String, datainputs: Map[String, Seq[Map[String, Any]]]) = {
    val data_list = datainputs.getOrElse("variable", List()).map(DataContainer(_)).flatten.toList
    val domain_list = datainputs.getOrElse("domain", List()).map(DomainContainer(_)).flatten.toList
    val operation_list = datainputs.getOrElse("operation", List()).map(WorkflowContainer(_)).flatten.toList
    new TaskRequest(process_name, data_list, domain_list, operation_list)
  }
}

class ContainerBase {
  def item_key(map_item: (String, Any)): String = map_item._1

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

  def getFloatValue( opt_val: Option[Any] ): Float = {
    opt_val match {
      case Some(p) => p match {
        case ix: Int => ix.toFloat;
        case fx: Float => fx.toFloat;
        case dx: Double => dx.toFloat;
        case sx: Short => sx.toFloat;
        case _ =>  Float.NaN;
      }
      case None => Float.NaN
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
  val fv = c.getFloatValue( tval )
  println( fv )
}


class DataContainer(val id: String, val dset: Any, val domain: Any) extends ContainerBase {
  override def toString = {
    s"DataContainer { id = $id, dset = $dset, domain = $domain }"
  }

  override def toXml() = {
      <dataset id={id} dset={dset.toString} domain={domain.toString}/>
  }
}

object DataContainer extends ContainerBase {
  def apply(metadata: Map[String, Any]): Option[DataContainer] = {
    try {
      val dset = filterMap(metadata, key_equals("dset"))
      val id = filterMap(metadata, key_equals("id"))
      val domain = filterMap(metadata, key_equals("domain"))
      Some(new DataContainer(id.toString, dset, domain))
    } catch {
      case e: Exception => {
        Logger.error("Error creating DataContainer: " + e.toString  )
        Logger.error( e.getStackTrace.mkString("\n") )
        None
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
      case axis_map: Map[String,Any] =>
        val start = getFloatValue( axis_map.get("start") )
        val end = getFloatValue( axis_map.get("end") )
        val system = getStringValue( axis_map.get("system") )
        val bounds = getStringValue( axis_map.get("bounds") )
        Some( new DomainAxis( id, start, end, system, bounds ) )
      case None => None
      case _ =>
        Logger.error("Unrecognized DomainAxis type: " + axis_spec.getClass.toString )
        None
    }
  }
}

class DomainAxis( val d_id: String, val d_start: Float, val d_end: Float, val d_system: String, val d_bounds: String ) extends ContainerBase  {

  override def toString = {
    s"DomainAxis { id = $d_id, start = $d_start, end = $d_end, system = $d_system, bounds = $d_bounds }"
  }

  override def toXml() = {
    <axis id={d_id} start={d_start.toString} end={d_end.toString} system={d_system} bounds={d_bounds} />
  }
}

object DomainContainer extends ContainerBase {
  def apply(metadata: Map[String, Any]): Option[DomainContainer] = {
    var items = new ListBuffer[Option[DomainAxis]]()
    try {
      val id = filterMap(metadata, key_equals("id"))
      items += DomainAxis("lat", filterMap(metadata, key_equals( """lat*""".r)))
      items += DomainAxis("lon", filterMap(metadata, key_equals( """lon*""".r)))
      items += DomainAxis("lev", filterMap(metadata, key_equals( """lev*""".r)))
      items += DomainAxis("time", filterMap(metadata, key_equals( """tim*""".r)))
      Some( new DomainContainer( id.toString, items.flatten.toList ) )
    } catch {
      case e: Exception => {
        Logger.error("Error creating DomainContainer: " + e.toString)
        Logger.error( e.getStackTrace.mkString("\n") )
        None
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
  def apply(metadata: Map[String, Any]): Option[WorkflowContainer] = {
    try {
      import nccs.utilities.wpsOperationParser
      val parsed_data_inputs = wpsOperationParser.parseOp(metadata("unparsed").toString)
      Some(new WorkflowContainer(parsed_data_inputs.flatMap(OperationContainer(_))))
    } catch {
      case e: Exception => {
        Logger.error("Error creating WorkflowContainer: " + e.toString)
        None
      }
    }
  }
}

class OperationContainer(val id: String, val result_id: String = "", val args: Iterable[Any])  extends ContainerBase {
  override def toString = {
    s"OperationContainer { id = $id, result_id = $result_id, args = $args }"
  }
  override def toXml() = {
    <proc id={id} result_id={result_id} args={args.toString}/>
  }
}

object OperationContainer extends ContainerBase {
  def apply(raw_metadata: Any): Option[OperationContainer] = {
    raw_metadata match {
      case ((result_id: String, id: String), args: Iterable[Any]) => Some(new OperationContainer(result_id, id, args))
      case (id: String, args: Iterable[Any]) => Some(new OperationContainer(id = id, args = args))
      case _ => {
        Logger.error("Unrecognized format for OperationContainer: " + raw_metadata.toString)
        None
      }
    }
  }
}


class TaskProcessor {

}
