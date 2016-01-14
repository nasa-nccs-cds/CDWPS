package nccs.engine
import nccs.process.TaskRequest
import org.apache.spark.{SparkContext, SparkConf}
import play.api.Logger

object SparkEngine {

  lazy val conf = {
    new SparkConf(false)
      .setMaster("local[*]")
      .setAppName("cdas")
      .set("spark.logConf", "true")
  }

  lazy val sc = SparkContext.getOrCreate(conf)

  def execute( request: TaskRequest, run_args: Map[String,Any] ) = {
    Logger.debug("Execute { request: " + request.toString + ", runargs: " + run_args.toString + "}"  )
  }
}
