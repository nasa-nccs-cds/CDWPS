package nccs.engine
import nccs.process.TaskRequest
import org.apache.spark.{SparkContext, SparkConf}
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object SparkEngine {
  val logger = LoggerFactory.getLogger("wps") // classOf[ContainerBase])

  lazy val conf = {
    new SparkConf(false)
      .setMaster("local[*]")
      .setAppName("cdas")
      .set("spark.logConf", "true")
  }

  lazy val sc = SparkContext.getOrCreate(conf)

  def execute( request: TaskRequest, run_args: Map[String,Any] ) = {
    logger.info("Execute { request: " + request.toString + ", runargs: " + run_args.toString + "}"  )
  }
}
