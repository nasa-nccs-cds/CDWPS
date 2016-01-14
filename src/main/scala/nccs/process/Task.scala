package nccs.process
import play.api.Logger
import scala.util.matching.Regex
import scala.reflect._

object TaskRequest {
//  val logger: Logger = Logger( this.getClass() )
  def apply(process_name: String, datainputs: collection.immutable.Map[ String, Seq[ collection.immutable.Map[String, Any]] ]) = {
    val data_list = datainputs.getOrElse( "variable", List() ).map( DataContainer(_) ).flatten.toList
    val domain_list = datainputs.getOrElse("domain", List() ).map( DomainContainer(_) ).flatten.toList
    val operation_list = datainputs.getOrElse("operation", List() ).map( WorkflowContainer(_) ).flatten.toList
    new TaskRequest(process_name, data_list, domain_list, operation_list )
  }
}

class ContainerBase {
  def item_key( map_item: (String, Any) ): String = map_item._1.stripPrefix("\"").stripSuffix("\"").toLowerCase
  def key_equals( key_value: String)(map_item: (String, Any) ): Boolean = { item_key(map_item) == key_value }
  def key_equals( key_regex: Regex )( map_item: (String, Any) ): Boolean = { key_regex.findFirstIn( item_key(map_item) ) match { case Some(x) => true; case None => false; } }
//  def key_equals( key_expr: Iterable[Any] )( map_item: (String, Any) ): Boolean = { key_expr.map( key_equals(_)(map_item) ).find( ((x:Boolean) => x) ) }
  def filterMap( raw_metadata: Map[String,Any], key_matcher: (((String, Any)) => Boolean)): Any = { raw_metadata.find(key_matcher) match { case Some(x) => x._2; case None => None } }
}

class DataContainer( val id: String, val dset: Any, val domain: Any ) extends ContainerBase {
  override def toString = { s"DataContainer { id = $id, dset = $dset, domain = $domain }" }
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
        Logger.error("Error creating DataContainer: " + e.toString)
        None
      }
    }
  }
}

class DomainContainer( val id: String, val axes: Map[String,Any] ) extends ContainerBase {
  override def toString = { s"DataContainer { id = $id, axes = $axes }" }
}

object DomainContainer extends ContainerBase {
  def apply(metadata: Map[String, Any]): Option[DomainContainer] = {
    var items = new scala.collection.mutable.ListBuffer[(String, Any)]()
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
}

class WorkflowContainer( val operations: Iterable[OperationContainer] ) extends ContainerBase  {
  override def toString = { s"WorkflowContainer { operations = $operations }" }
}

object WorkflowContainer {
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

class TaskRequest(val name: String, val data: List[DataContainer], domain: List[DomainContainer], val operation: List[WorkflowContainer]) {
  override def toString = { s"TaskRequest { data = '$data', domain='$domain', operation='$operation' }" }
}

class TaskProcessor {

}
