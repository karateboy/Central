package controllers

import com.github.nscala_time.time.Imports._
import models.ModelHelper.errorHandler
import models._
import play.api.libs.json.Json
import play.api.mvc.Controller

import java.util.Date
import javax.inject.Inject
import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
class AisDataController @Inject()(aisDB: AisDB, monitorDB: MonitorDB, recordDB: RecordDB, monitorTypeOp: MonitorTypeDB)extends Controller{

  case class Position(lat:Double, lng:Double, date:Option[Date])
  case class ShipData(name:String, route:Seq[Position])
  case class ShipRouteResult(monitorRecords: Seq[RecordList], shipDataList:Seq[ShipData])
  def getShipRoute(monitor:String, tabTypeStr:String, ais:Boolean, start:Long, end:Long)= Security.Authenticated.async {
    val tabType = TableType.withName(tabTypeStr)
    val startTime = new DateTime(start)
    val endTime = new DateTime(end)
    val monitorRecordF = recordDB.getRecordListFuture(TableType.mapCollection(tabType))(startTime = startTime, endTime = endTime, Seq(monitor))
    monitorRecordF onFailure errorHandler
    val aisDataListF = if(ais)
      aisDB.getAisDataList(monitor, aisDB.respSimpleType, startTime.toDate, endTime.toDate)
    else
      Future.successful(Seq.empty[AisData])

    aisDataListF onFailure errorHandler

    val shipRouteMap = scala.collection.mutable.Map.empty[String, ListBuffer[Position]]

    for(monitorRecord<-monitorRecordF;aisDataList<-aisDataListF) yield {
      val filteredAisData =
        if(tabType == TableType.hour) {
          aisDataList.groupBy(aisData=>{
            new DateTime(aisData.time).withMinuteOfHour(0)
              .withSecondOfMinute(0).withMillisOfSecond(0)
          }).map(pair=>pair._2.head).toList.sortBy(aisData=>aisData.time).reverse
        } else
          aisDataList

      filteredAisData
        .foreach(aisData=>{
        val shipList = Json.parse(aisData.json).validate[Seq[Map[String, String]]].get
        shipList
          .filter(shipMap=>shipMap.contains("MMSI") && shipMap.contains("LAT") && shipMap.contains("LON"))
          .foreach(shipMap=>{
            val shipRoute = shipRouteMap.getOrElseUpdate(shipMap("MMSI"), ListBuffer.empty[Position])
            val lat = shipMap("LAT").toDouble
            val lng = shipMap("LON").toDouble
            shipRoute.append(Position(lat = lat, lng=lng))
          })
      })
      implicit val w3 = Json.writes[Position]
      implicit val w2 = Json.writes[ShipData]
      implicit val w1 = Json.writes[ShipRouteResult]
      val shipDataList = shipRouteMap.map(ship=> ShipData(ship._1, ship._2)).toSeq
      Ok(Json.toJson(ShipRouteResult(monitorRecord.reverse, shipDataList)))
    }
  }
}
