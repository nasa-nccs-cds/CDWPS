package demo

import play.api.mvc._

class HelloController extends Controller {
  val dataFile = "resources/words.text"
  lazy val rdd = SparkCommons.sc.textFile(dataFile)

  def index = Action {
    Ok("hello world")
  }

  def count = Action {
    val counts = rdd.flatMap(line => line.split(" ")).map(word => (word, 1)).reduceByKey(_ + _)
    Ok( counts.collect().mkString(",") )
  }
}

