package models

import models.MonitorType._
import org.mongodb.scala.result.UpdateResult
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait MonitorTypeDB {
  implicit val configWrite = Json.writes[ThresholdConfig]
  implicit val configRead = Json.reads[ThresholdConfig]
  implicit val mtWrite = Json.writes[MonitorType]
  implicit val mtRead = Json.reads[MonitorType]
  val defaultMonitorTypes = List(
    rangeType(SO2, "二氧化硫", "ppb", 2),
    rangeType(NOX, "氮氧化物", "ppb", 2),
    rangeType(NO2, "二氧化氮", "ppb", 2),
    rangeType(NO, "一氧化氮", "ppb", 2),
    rangeType(NOY, "NOY", "ppb", 2),
    rangeType(NOY_NO, "NOY-NO", "ppb", 2),
    rangeType(CO, "一氧化碳", "ppm", 2),
    rangeType(CO2, "二氧化碳", "ppm", 2),
    rangeType(O3, "臭氧", "ppb", 2),
    rangeType(THC, "總碳氫化合物", "ppm", 2),
    rangeType(TS, "總硫", "ppb", 2),
    rangeType(CH4, "甲烷", "ppm", 2),
    rangeType(NMHC, "非甲烷碳氫化合物", "ppm", 2),
    rangeType(NH3, "氨", "ppb", 2),
    rangeType("TSP", "TSP", "μg/m3", 2),
    rangeType(PM10, "PM10懸浮微粒", "μg/m3", 2),
    rangeType(PM25, "PM2.5細懸浮微粒", "μg/m3", 2),
    rangeType(WIN_SPEED, "風速", "m/sec", 2),
    rangeType(WIN_DIRECTION, "風向", "degrees", 2),
    rangeType(TEMP, "溫度", "℃", 2),
    rangeType(HUMID, "濕度", "%", 2),
    rangeType(PRESS, "氣壓", "hPa", 2),
    rangeType(RAIN, "雨量", "mm/h", 2),
    rangeType(LAT, "緯度", "度", 5),
    rangeType(LNG, "經度", "度", 5),
    rangeType("RT", "室內溫度", "℃", 1),
    rangeType("O2", "氧氣 ", "%", 1),
    rangeType(SOLAR, "日照", "W/m2", 2),
    rangeType(CH2O, "CH2O", "ppb", 2),
    rangeType(TVOC, "TVOC", "ppb", 2),
    rangeType(NOISE, "NOISE", "dB", 2),
    rangeType(H2S, "H2S", "ppb", 2),
    rangeType(H2, "H2", "ppb", 2),
    /////////////////////////////////////////////////////
    signalType(DOOR, "門禁"),
    signalType(SMOKE, "煙霧"),
    signalType(FLOW, "採樣流量"),
    signalType("SPRAY", "灑水"))

  val mtToEpaMtMap: Map[String, String] = Map(
    MonitorType.TEMP -> "14",
    MonitorType.CH4 -> "31",
    MonitorType.CO -> "02",
    MonitorType.CO2 -> "36",
    MonitorType.NMHC -> "09",
    MonitorType.NO -> "06",
    MonitorType.NO2 -> "07",
    MonitorType.NOX -> "05",
    MonitorType.O3 -> "03",
    MonitorType.PH_RAIN -> "21",
    MonitorType.PM10 -> "04",
    MonitorType.PM25 -> "33",
    MonitorType.RAIN -> "23",
    MonitorType.SO2 -> "01",
    MonitorType.THC -> "08",
    MonitorType.WIN_DIRECTION -> "11",
    MonitorType.WIN_SPEED -> "10")

  val epaToMtMap: Map[Int, String] = mtToEpaMtMap.map(pair => pair._2.toInt -> pair._1)
  val epaMonitorTypes = mtToEpaMtMap.keys.toList

  var mtvList = List.empty[String]
  var signalMtvList = List.empty[String]
  var map = Map.empty[String, MonitorType]
  var diValueMap = Map.empty[String, Boolean]

  def signalType(_id: String, desp: String): MonitorType = {
    signalOrder += 1
    MonitorType(_id, desp, "N/A", 0, signalOrder, true)
  }

  def logDiMonitorType(alarmDB: AlarmDB, mt: String, v: Boolean): Unit = {
    if (!signalMtvList.contains(mt))
      Logger.warn(s"${mt} is not DI monitor type!")

    val previousValue = diValueMap.getOrElse(mt, !v)
    diValueMap = diValueMap + (mt -> v)
    if (previousValue != v) {
      val mtCase = map(mt)
      if (v)
        alarmDB.log(alarmDB.src(), alarmDB.Level.WARN, s"${mtCase.desp}=>觸發", 1)
      else
        alarmDB.log(alarmDB.src(), alarmDB.Level.INFO, s"${mtCase.desp}=>解除", 1)
    }
  }

  protected def refreshMtv(): Unit = {
    val list = getList.sortBy {
      _.order
    }
    val mtPair =
      for (mt <- list) yield {
        try {
          val mtv = mt._id
          mtv -> mt
        } catch {
          case _: NoSuchElementException =>
            mt._id -> mt
        }
      }

    val rangeList = list.filter { mt => mt.signalType == false }
    val rangeMtvList = rangeList.map(mt => (mt._id))
    val signalList = list.filter { mt => mt.signalType }
    val signalMvList = signalList.map(mt => (mt._id))
    mtvList = rangeMtvList
    signalMtvList = signalMvList
    map = mtPair.toMap
  }

  def getList: List[MonitorType]

  def ensure(id: String): Unit = {
    synchronized {
      if (!map.contains(id)) {
        val mt = rangeType(id, id, "??", 2)
        mt.measuringBy = Some(List.empty[String])
        upsertMonitorType(mt)
      } else {
        val mtCase = map(id)
        if (mtCase.measuringBy.isEmpty) {
          mtCase.measuringBy = Some(List.empty[String])
          upsertMonitorType(mtCase)
        }
      }
    }
  }

  def ensure(mtCase: MonitorType): Unit = {
    synchronized {
      if (!map.contains(mtCase._id)) {
        mtCase.measuringBy = Some(List.empty[String])
        upsertMonitorType(mtCase)
      } else {
        if (mtCase.measuringBy.isEmpty) {
          mtCase.measuringBy = Some(List.empty[String])
          upsertMonitorType(mtCase)
        }
      }
    }
  }

  def rangeType(_id: String, desp: String, unit: String, prec: Int, accumulated: Boolean = false): MonitorType = {
    rangeOrder += 1
    MonitorType(_id, desp, unit, prec, rangeOrder, accumulated = Some(accumulated))
  }

  def calculatedType(_id: String, desp: String, unit: String, prec: Int): MonitorType = {
    rangeOrder += 1
    MonitorType(_id, desp, unit, prec, rangeOrder, calculated = Some(true))
  }

  def deleteMonitorType(_id: String) = {
    synchronized {
      if (map.contains(_id)) {
        val mt = map(_id)
        map = map - _id
        if (mt.signalType)
          signalMtvList = signalMtvList.filter(p => p != _id)
        else
          mtvList = mtvList.filter(p => p != _id)

        deleteItemFuture(_id)
      }
    }
  }

  def deleteItemFuture(_id: String): Unit

  def allMtvList: List[String] = mtvList ++ signalMtvList

  def activeMtvList: List[String] = mtvList.filter { mt => map(mt).measuringBy.isDefined }

  def addMeasuring(mt: String, instrumentId: String, append: Boolean, recordDB: RecordDB): Future[UpdateResult] = {
    recordDB.ensureMonitorType(mt)
    synchronized {
      if (!map.contains(mt)) {
        val mtCase = rangeType(mt, mt, "??", 2)
        mtCase.addMeasuring(instrumentId, append)
        upsertMonitorType(mtCase)
      } else {
        val mtCase = map(mt)
        mtCase.addMeasuring(instrumentId, append)
        upsertItemFuture(mtCase)
      }
    }
  }

  def upsertMonitorType(mt: MonitorType): Future[UpdateResult] = {
    synchronized {
      map = map + (mt._id -> mt)
      if (mt.signalType) {
        if (!signalMtvList.contains(mt._id))
          signalMtvList = signalMtvList :+ mt._id
      } else {
        if (!mtvList.contains(mt._id))
          mtvList = mtvList :+ mt._id
      }

      upsertItemFuture(mt)
    }
  }

  protected def upsertItemFuture(mt: MonitorType): Future[UpdateResult]

  def stopMeasuring(instrumentId: String): Future[Seq[UpdateResult]] = {
    val mtSet = realtimeMtvList.toSet ++ signalMtvList.toSet
    val allF: Seq[Future[UpdateResult]] =
      for {mt <- mtSet.toSeq
           instrumentList <- map(mt).measuringBy if instrumentList.contains(instrumentId)
           } yield {
        val newMt = map(mt).stopMeasuring(instrumentId)
        map = map + (mt -> newMt)
        upsertItemFuture(newMt)
      }
    Future.sequence(allF)
  }

  def realtimeMtvList: List[String] = mtvList.filter { mt =>
    val measuringBy = map(mt).measuringBy
    measuringBy.isDefined && (!measuringBy.get.isEmpty)
  }

  def format(mt: String, v: Option[Double]): String = {
    if (v.isEmpty)
      "-"
    else {
      val prec = map(mt).prec
      s"%.${prec}f".format(v.get)
    }
  }

  def formatRecord(mt: String, r: Option[Record]): String = {
    val ret =
      for (rec <- r if rec.value.isDefined) yield {
        val prec = map(mt).prec
        s"%.${prec}f".format(r.get.value.get)
      }
    ret.getOrElse("-")
  }

  def getCssClassStr(record: MtRecord): Seq[String] = {
    val (overInternal, overLaw) = overStd(record.mtName, record.value)
    MonitorStatus.getCssClassStr(record.status, overInternal, overLaw)
  }

  def getCssClassStr(mt: String, r: Option[Record]): Seq[String] = {
    if (r.isEmpty)
      Seq.empty[String]
    else {
      val v = r.get.value
      val (overInternal, overLaw) = overStd(mt, v)
      MonitorStatus.getCssClassStr(r.get.status, overInternal, overLaw)
    }
  }

  def overStd(mt: String, vOpt: Option[Double]): (Boolean, Boolean) = {
    val mtCase = map(mt)

    val overLaw =
      for (std <- mtCase.std_law; v <- vOpt) yield
        if (v > std)
          true
        else
          false
    (overLaw.getOrElse(false), overLaw.getOrElse(false))
  }

  type MtCalculator = (Seq[RecordList], Integer) => Unit

  def directionCalculator: MtCalculator = (recordLists, pos) => {
    val neededMonitorTypes = Seq(MonitorType.LAT, MonitorType.LNG)
    val currentRecordList = recordLists(pos)

    def checkTypes: Boolean = {
      (pos >= 1 && pos < recordLists.length) &&
        recordLists.drop(pos - 1).take(2).forall(recordList => {
          val mtMap = recordList.mtMap
          neededMonitorTypes.forall(mt =>
            mtMap.contains(mt) &&
              mtMap(mt).status == MonitorStatus.NormalStat &&
              mtMap(mt).value.isDefined)
        })
    }

    val newMtRecord =
      if (!checkTypes)
        MtRecord(MonitorType.DIRECTION, None, MonitorStatus.NormalStat)
      else {
        val lastMtMap = recordLists(pos - 1).mtMap
        val mtMap = recordLists(pos).mtMap
        val lat1 = lastMtMap(MonitorType.LAT).value.get
        val lng1 = lastMtMap(MonitorType.LNG).value.get
        val lat2 = mtMap(MonitorType.LAT).value.get
        val lng2 = mtMap(MonitorType.LNG).value.get
        val dy = lat2 - lat1
        val dx = Math.cos(Math.PI / 180 * lat1) * (lng2 - lng1)

        val degree = if (Math.abs(dx) <= 0.0001 && Math.abs(dy) <= 0.0001)
          0.0d
        else
          Math.toDegrees(Math.atan2(dy, dx))

        if (degree >= 0)
          MtRecord(MonitorType.DIRECTION, Some(degree), MonitorStatus.NormalStat)
        else
          MtRecord(MonitorType.DIRECTION, Some(degree + 360), MonitorStatus.NormalStat)
      }
    currentRecordList.mtDataList = currentRecordList.mtDataList :+ newMtRecord
  }


  def winDirectionOffsetCalculator: MtCalculator = (recordLists, pos) => {
    val neededMonitorTypes = Seq(MonitorType.DIRECTION, MonitorType.WIN_DIRECTION)
    val currentRecordList = recordLists(pos)
    val mtMap = currentRecordList.mtMap

    def checkTypes: Boolean =
      neededMonitorTypes.forall(mt =>
        mtMap.contains(mt) &&
          mtMap(mt).status == MonitorStatus.NormalStat &&
          mtMap(mt).value.isDefined)

    val newMtRecord =
      if (!checkTypes)
        MtRecord(MonitorType.WIND_DIRECTION_OFFSET, None, MonitorStatus.NormalStat)
      else {
        val value =
          for (windDir <- mtMap(MonitorType.WIN_DIRECTION).value; dir <- mtMap(MonitorType.DIRECTION).value) yield
            Math.abs(Math.toDegrees(Math.toRadians(dir - windDir)))

        val adjust = value map(v=>{
          if(v <= 180)
            v
          else
            360 - v
        })
        MtRecord(MonitorType.WIND_DIRECTION_OFFSET, adjust, MonitorStatus.NormalStat)
      }

    currentRecordList.mtDataList = currentRecordList.mtDataList :+ newMtRecord
  }

  val calculatedMonitorTypes = Seq(
    calculatedType(MonitorType.DIRECTION, "航向", "度", 2),
    calculatedType(MonitorType.WIND_DIRECTION_OFFSET, "相對風向與航向夾角", "度", 2)
  )

  val monitorTypeCalculatorMap: Map[String, MtCalculator] = Map(
    MonitorType.DIRECTION -> directionCalculator,
    MonitorType.WIND_DIRECTION_OFFSET -> winDirectionOffsetCalculator
  )

  def ensureCalculatedMonitorTypes(): Unit = {
    calculatedMonitorTypes.filter(mt => {
      !map.contains(mt._id)
    }).foreach(ensure)
  }

  def appendCalculatedMtRecord(recordLists:Seq[RecordList], mtList:Seq[String]): Unit = {
    if (mtList.find(monitorTypeCalculatorMap.contains).nonEmpty) {
      for {pos <- scala.collection.immutable.Range(0, recordLists.length)
           mtCase <- calculatedMonitorTypes
           calculator = monitorTypeCalculatorMap(mtCase._id)} {
        val recordList = recordLists(pos)
        if(recordList.mtDataList.find(mtRecord=>mtRecord.mtName == mtCase._id).isEmpty)
          calculator(recordLists, pos)
      }
    }
  }
}
