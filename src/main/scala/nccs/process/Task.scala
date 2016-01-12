package nccs.process

class Logger(logFilePath: String) {
  import java.io._
  val logfile = new File(logFilePath)
  val writer = new FileWriter(logfile)
  def info(text: String): Unit = writer.write("INFO: " + text + "\n")
  def close(): Unit = writer.close()
}

object taskLogger extends Logger("/tmp/cdas_task.log")

object TaskRequest {
  def create(process_name: String, datainputs: Map[String, Any]) = {
    taskLogger.info("TaskRequest: create")
    val data = new DataContainer(datainputs.getOrElse("data", Map()))
    val domain = new DomainContainer(datainputs.getOrElse("domain", Map()))
    val operation = new WorkflowContainer(datainputs.getOrElse("operation", List()))
    new TaskRequest(process_name, data, domain, operation)
  }
}

class ContainerBase {
  def key_equals(key_value: String)(map_item: (String, Any)): Boolean = (map_item._1 == key_value)
  def filterMap(raw_metadata: Any, key_matcher: (((String, Any)) => Boolean)) = {
    taskLogger.info("filterMap, raw: " + raw_metadata)
    raw_metadata match {
      case map_metadata: Map[String, Any] =>
        taskLogger.info("filterMap, map: " + map_metadata)
        map_metadata.find(key_matcher)
      case _ => None
    }
  }
}

class DataContainer(raw_metadata: Any) extends ContainerBase {
  taskLogger.info("DataContainer, raw_metadata: " + raw_metadata)
  val dset = filterMap(raw_metadata, key_equals("dset"))
  val id = filterMap(raw_metadata, key_equals("id"))
  val domain = filterMap(raw_metadata, key_equals("domain"))

  override def toString = { s"DataContainer { id = '$id', dset='$dset', domain='$domain' }" }
}

class DomainContainer(raw_metadata: Any) {
}

class WorkflowContainer(raw_metadata: Any) {
}

class TaskRequest(val name: String, val data: DataContainer, domain: DomainContainer, val operation: WorkflowContainer) {

  override def toString = { s"TaskRequest { data = '$data', domain='$domain', operation='$operation' }" }
}

class TaskProcessor {

}
