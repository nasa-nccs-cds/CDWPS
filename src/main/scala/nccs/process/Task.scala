package nccs.process
import play.api.Logger
import scala.util.matching.Regex

object TaskRequest {
//  val logger: Logger = Logger( this.getClass() )
  def create(process_name: String, datainputs: Map[String, Any]) = {
    Logger.debug("TaskRequest: datainput = " + datainputs.toString + " regex class: " + """lat*""".r.getClass.getName  )
    val data = DataContainer(datainputs.get("variable" ) )
    val domain = DomainContainer( datainputs.get("domain") )
    val operation = WorkflowContainer( datainputs.get("operation") )
    new TaskRequest(process_name, data, domain, operation)
  }
}

class ContainerBase {
  def item_key( map_item: (String, Any) ): String = map_item._1.stripPrefix("\"").stripSuffix("\"").toLowerCase
  def key_equals( key_value: String)(map_item: (String, Any) ): Boolean = { item_key(map_item) == key_value }
  def key_equals( key_regex: Regex )( map_item: (String, Any) ): Boolean = { key_regex.findFirstIn( item_key(map_item) ) match { case Some(x) => true; case None => false; } }
//  def key_equals( key_expr: Iterable[Any] )( map_item: (String, Any) ): Boolean = { key_expr.map( key_equals(_)(map_item) ).find( ((x:Boolean) => x) ) }
  def filterMap( raw_metadata: Map[String, Any], key_matcher: (((String, Any)) => Boolean)) = { raw_metadata.find(key_matcher) match { case Some(x) => x._2; case None => None } }
}

class DataContainer( val id: Any = None, val dset: Any = None, val domain: Any = None ) extends ContainerBase {
  override def toString = { s"DataContainer { id = $id, dset = $dset, domain = $domain }" }
}
object DataContainer extends DataContainer {
  def apply(raw_metadata: Any) : DataContainer = {
    raw_metadata match {
      case Some(metadata: Map[String, Any]) =>
        val dset = filterMap(metadata, key_equals("dset"))
        val id = filterMap(metadata, key_equals("id"))
        val domain = filterMap(metadata, key_equals("domain"))
        new DataContainer(id, dset, domain)
      case _ =>
        new DataContainer()
    }
  }
}

class DomainContainer( val id: Any = None, val axes: Map[String,Any] = None ) extends ContainerBase {
  override def toString = { s"DataContainer { id = $id, axes = $axes }" }
}
object DomainContainer extends DataContainer {
  def apply(raw_metadata: Any): DomainContainer = {
    var items = new ListBuffer[(String,Any)]()
    raw_metadata match {
      case Some(metadata: Map[String, Any]) =>
        val id = filterMap(metadata, key_equals("id"))
        items +=("lat", filterMap(metadata, key_equals( """lat*""".r)))
        items +=("lon", filterMap(metadata, key_equals( """lon*""".r)))
        items +=("lev", filterMap(metadata, key_equals( """lev*""".r)))
        items +=("time", filterMap(metadata, key_equals( """tim*""".r)))
        new DomainContainer( id, items )
      case _ =>
        new DomainContainer()
    }
  }
}

case class OperationContainer( op_spec: Any ) {
  true
}

class WorkflowContainer( raw_metadata: Any ) extends ContainerBase  {
  raw_metadata match {
    case Some( metadata: List[ Any ] ) =>
      val operations: scala.collection.mutable.List[OperationContainer] = metadata.map( new OperationContainer(_) )
    case _ => None
  }
  override def toString = { s"WorkflowContainer { operations = $operations }" }
}

class TaskRequest(val name: String, val data: DataContainer, domain: DomainContainer, val operation: WorkflowContainer) {

  override def toString = { s"TaskRequest { data = '$data', domain='$domain', operation='$operation' }" }
}

class TaskProcessor {

}
