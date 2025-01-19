import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types.{StructType, StringType, IntegerType, TimestampType, LongType}
import org.apache.spark.sql.functions.{from_json, col}

object KafkaSparkApp {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession.builder()
      .appName("KafkaSparkApp")
      .master("local[*]")
      .config("spark.sql.extensions", "org.apache.iceberg.spark.extensions.IcebergSparkSessionExtensions")
      .config("spark.sql.catalog.spark_catalog", "org.apache.iceberg.spark.SparkSessionCatalog")
      .config("spark.sql.catalog.spark_catalog.type", "hadoop")
      .config("spark.sql.catalog.spark_catalog.warehouse", "s3a://league-of-legend-iceberg/ice-berg")
      .getOrCreate()

    val hadoopConf = spark.sparkContext.hadoopConfiguration
    hadoopConf.set("fs.s3a.access.key", "minioadmin")
    hadoopConf.set("fs.s3a.secret.key", "minioadmin")
    hadoopConf.set("fs.s3a.endpoint", "http://localhost:13579")
    hadoopConf.set("fs.s3a.path.style.access", "true")
    hadoopConf.set("fs.s3a.impl", "org.apache.hadoop.fs.s3a.S3AFileSystem")
    hadoopConf.set("fs.s3a.connection.ssl.enabled", "false")

    val kafkaBrokers = "127.0.0.1:9092,127.0.0.1:9093,127.0.0.1:9094"

    val df = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaBrokers)
      .option("subscribe", "league-of-legend")
      .option("startingOffsets", "latest")
      .option("failOnDataLoss", "false")
      .load()

    import spark.implicits._
    val jsonSchema = new StructType()
        .add("gameID", StringType)
        .add("uniqueId", StringType)
        .add("email", StringType)
        .add("password", StringType)
        .add("tier", StringType)
        .add("sex", StringType)
        .add("age", StringType)
        .add("nickName", StringType)
        .add("team", StringType)
        .add("ip", StringType)
        .add("champion", StringType)
        .add("kill", StringType)
        .add("death", StringType)
        .add("assist", StringType)
        .add("gamePlayTime", StringType)
        .add("isWin", StringType)
        .add("createGameDate", StringType)

    /*
    val messages = df.withColumn("jsonString", col("value").cast(StringType))
    messages.printSchema()

    val parsedMessages = messages.select(from_json($"jsonString", jsonSchema).as("data")).select("data.*").coalesce(1)

    val minioOutputPath = "s3a://ice-berg/test"

    val query = parsedMessages.writeStream
      .outputMode("append")
      .format("parquet")
      .option("checkpointLocation", "s3a://ice-berg/test")
      .option("maxRecordsPerFile", 10000)
      .trigger(Trigger.ProcessingTime("10 seconds"))
      .start(minioOutputPath)
    */

    val messages = df.withColumn("jsonString", col("value").cast(StringType))

    val parsedMessages = messages.select(from_json(col("jsonString"), jsonSchema).as("data")).select("data.*")

    spark.sql("CREATE NAMESPACE IF NOT EXISTS lol_db")

    spark.sql("""
        CREATE TABLE IF NOT EXISTS lol_db.game_table (
          gameID STRING,
          uniqueId STRING,
          email STRING,
          password STRING,
          tier STRING,
          sex STRING,
          age STRING,
          nickName STRING,
          team STRING,
          ip STRING,
          champion STRING,
          kill STRING,
          death STRING,
          assist STRING,
          gamePlayTime STRING,
          isWin STRING,
          createGameDate STRING
        )
        USING iceberg
        LOCATION 's3a://league-of-legend-iceberg/ice-berg/lol_db/game_table'
        PARTITIONED BY (tier)
        TBLPROPERTIES (
              'write.merge.mode' = 'merge-on-read'
           )
      """)

    val query = parsedMessages.writeStream
      .format("iceberg")
      .outputMode("append")
      .option("checkpointLocation", "/tmp/iceberg-checkpoint")  // 체크포인트 경로 설정
      .option("maxRecordsPerFile", 100)  // 파일당 최대 레코드 수 제한
      .partitionBy("createGameDate")  // createGameDate 로 파티셔닝
      .trigger(Trigger.ProcessingTime("5 seconds"))  // 트리거 간격을 5분으로 설정
      .toTable("lol_db.game_table")

    query.awaitTermination()
  }
}