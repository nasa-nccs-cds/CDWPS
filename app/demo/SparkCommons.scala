package demo

import org.apache.spark.{SparkContext, SparkConf}

object SparkCommons {
  lazy val conf = {
    new SparkConf(false)
      .setMaster("local[*]")
      .setAppName("play demo")
      .set("spark.logConf", "true")
  }

  lazy val sc = SparkContext.getOrCreate(conf)

}
