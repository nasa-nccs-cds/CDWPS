package nccs.process
import play.api.Logger
import scala.util.matching.Regex

object TaskRequest {
//  val logger: Logger = Logger( this.getClass() )
  def apply(process_name: String, datainputs: Map[ String, Any ]) = {
    Logger.debug("TaskRequest: datainput = " + datainputs.toString + " regex class: " + """lat*""".r.getClass.getName  )
    val data = DataContainer(datainputs.get("variable" ) )
    val domain = DomainContainer( datainputs.get("domain") )
    val operation = WorkflowContainer( datainputs.get("operation") )
    new TaskRequest(process_name, data, domain, operation)
  }
}

class ContainerBase {
  def item_key( map_item: (Any, Any) ): String = map_item._1.toString.stripPrefix("\"").stripSuffix("\"").toLowerCase
  def key_equals( key_value: String)(map_item: (Any, Any) ): Boolean = { item_key(map_item) == key_value }
  def key_equals( key_regex: Regex )( map_item: (Any, Any) ): Boolean = { key_regex.findFirstIn( item_key(map_item) ) match { case Some(x) => true; case None => false; } }
//  def key_equals( key_expr: Iterable[Any] )( map_item: (String, Any) ): Boolean = { key_expr.map( key_equals(_)(map_item) ).find( ((x:Boolean) => x) ) }
  def filterMap( raw_metadata: Map[Any,Any], key_matcher: (((Any, Any)) => Boolean)): Any = { raw_metadata.find(key_matcher) match { case Some(x) => x._2; case None => None } }
}

class DataContainer( val id: String, val dset: Any, val domain: Any ) extends ContainerBase {
  override def toString = { s"DataContainer { id = $id, dset = $dset, domain = $domain }" }
}

object DataContainer extends ContainerBase {
  def apply(metadata_opt: Option[Any]): Option[DataContainer] = {
    metadata_opt match {
      case Some(raw_metadata) => raw_metadata match {
        case metadata: Map[Any,Any] => {
          try {
            val dset = filterMap(metadata, key_equals("dset"))
            val id = filterMap(metadata, key_equals("id"))
            val domain = filterMap(metadata, key_equals("domain"))
            Some(new DataContainer(id.toString, dset, domain))
          } catch {
            case e: Exception => {
              Logger.error("Error creating DataContainer: " + e.toString)
              None
            }
          }
        }
        case None => {
          Logger.warn("Unrecognized DataContainer format: " + raw_metadata.toString)
          None
        }
      }
      case None => {
        Logger.warn("Empty DataContainer")
        None
      }
    }
  }
}

class DomainContainer( val id: String, val axes: Map[String,Any] ) extends ContainerBase {
  override def toString = { s"DataContainer { id = $id, axes = $axes }" }
}

object DomainContainer extends ContainerBase {
  def apply(metadata_opt: Option[Any]): Option[DomainContainer] = {
    var items = new scala.collection.mutable.ListBuffer[(String, Any)]()
    metadata_opt match {
      case Some(raw_metadata) => raw_metadata match {
        case metadata: Map[Any,Any] => {
          try {
            val id = filterMap(metadata, key_equals("id"))
            items += ("lat" -> filterMap(metadata, key_equals( """lat*""".r)))
            items += ("lon" -> filterMap(metadata, key_equals( """lon*""".r)))
            items += ("lev" -> filterMap(metadata, key_equals( """lev*""".r)))
            items += ("time" -> filterMap(metadata, key_equals( """tim*""".r)))
            Some(new DomainContainer(id.toString, items.toMap[String, Any]))
          } catch {
            case e: Exception => {
              Logger.error("Error creating DomainContainer: " + e.toString)
              None
            }
          }
        }
        case None => {
          Logger.warn("Unrecognized DomainContainer format: " + raw_metadata.toString)
          None
        }
      }
      case None => {
        Logger.warn("Enpty DomainContainer")
        None
      }
    }
  }
}

object WorkflowContainer {
  def apply(metadata_opt: Option[Any]): Option[WorkflowContainer] = {
    metadata_opt match {
      case Some(raw_metadata) => raw_metadata match {
        case metadata: List[Any] => {
          try {
            Some(new WorkflowContainer(metadata.flatMap(OperationContainer(_))))
          } catch {
            case e: Exception => {
              Logger.error("Error creating WorkflowContainer: " + e.toString)
              None
            }
          }
        }
        case None => {
          Logger.warn("Unrecognized DomainContainer format: " + raw_metadata.toString)
          None
        }
      }
      case None => {
        Logger.warn("Enpty DomainContainer")
        None
      }
    }
  }
}

class WorkflowContainer( val operations: Iterable[OperationContainer] ) extends ContainerBase  {
  override def toString = { s"WorkflowContainer { operations = $operations }" }
}

class OperationContainer( val id: String, val result_id: String="", val args: Iterable[Any] ) {
  override def toString = { s"OperationContainer { id = $id, result_id = $result_id, args = $args }" }
}
object OperationContainer extends ContainerBase {
  def apply(raw_metadata: Any): Option[OperationContainer] = {
    raw_metadata match {
      case ((result_id: String, id: String ), args:Iterable[Any]) => Some( new OperationContainer(result_id,id,args) )
      case ( id: String, args:Iterable[Any]) => Some( new OperationContainer( id=id, args=args ) )
      case _ => {
        Logger.error("Unrecognized format for OperationContainer: " + raw_metadata.toString )
        None
      }
    }
  }
}

class TaskRequest(val name: String, val data: Option[DataContainer], domain: Option[DomainContainer], val operation: Option[WorkflowContainer]) {
  override def toString = { s"TaskRequest { data = '$data', domain='$domain', operation='$operation' }" }
}

class TaskProcessor {

}
