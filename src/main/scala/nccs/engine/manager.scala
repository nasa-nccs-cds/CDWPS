package nccs.engine
import nccs.process.TaskRequest
import org.slf4j.LoggerFactory

class ExecutionManager {}

object ExecutionManager {
  val logger = LoggerFactory.getLogger( classOf[ExecutionManager] )

  def execute( request: TaskRequest, run_args: Map[String,Any] ) = {
    logger.info("Execute { request: " + request.toString + ", runargs: " + run_args.toString + "}"  )
  }
}
