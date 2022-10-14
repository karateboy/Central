package models.mongodb

import com.github.nscala_time.time.Imports._
import models.ModelHelper._
import models._
import org.mongodb.scala._
import org.mongodb.scala.bson.BsonString
import org.mongodb.scala.result.UpdateResult

import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class ManualAuditLogOp @Inject()(mongodb: MongoDB) extends ManualAuditLogDB {
  import org.bson.codecs.configuration.CodecRegistries.{fromProviders, fromRegistries}
  import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
  import org.mongodb.scala.bson.codecs.Macros._

  lazy val codecRegistry = fromRegistries(fromProviders(classOf[ManualAuditLog]), DEFAULT_CODEC_REGISTRY)
  lazy val colName = "auditLogs"
  lazy val collection: MongoCollection[ManualAuditLog] = mongodb.database.getCollection[ManualAuditLog](colName).withCodecRegistry(codecRegistry)

  import org.mongodb.scala.model._
  override def upsertLog(log: ManualAuditLog):Future[UpdateResult] = {
    import org.mongodb.scala.model.ReplaceOptions
    val f = collection.replaceOne(Filters.and(
      Filters.equal("dataTime", log.dataTime),
      Filters.equal("monitor", log.monitor),
      Filters.equal("mt", log.mt)), log, ReplaceOptions().upsert(true)).toFuture()

    f.onFailure(errorHandler)
    f
  }

  init

  import org.mongodb.scala.model.Filters._

  override def queryLog2(startTime: DateTime, endTime: DateTime): Future[Seq[ManualAuditLog]] = {
    import org.mongodb.scala.bson.BsonDateTime
    import org.mongodb.scala.model.Filters._
    import org.mongodb.scala.model.Sorts._

    val future = collection.find(and(gte("dataTime", startTime: BsonDateTime), lt("dataTime", endTime: BsonDateTime))).sort(ascending("dataTime")).toFuture()
    future
  }

  private def init() {
    import org.mongodb.scala.model.Indexes._
    for (colNames <- mongodb.database.listCollectionNames().toFuture()) {
      if (!colNames.contains(colName)) {
        val f = mongodb.database.createCollection(colName).toFuture()
        f.onFailure(errorHandler)
        f.onSuccess({
          case _ =>
            collection.createIndex(ascending("dataTime", "monitor","mt"))
        })
      }
    }
  }
}