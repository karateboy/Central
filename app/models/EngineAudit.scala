package models

import com.github.nscala_time.time.Imports
import com.github.nscala_time.time.Imports.DateTime
import play.api.Logger
import play.api.libs.json.Json

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object EngineAudit {
  case class EngineAuditSetting(directionOffsetThreshold: Double,
                                windSpeedThreshold: Double,
                                turnSpeedThreshold: Double,
                                turnDirectionThreshold: Double)

  case class EngineAuditParam(setting: EngineAuditSetting, monitors: Seq[String],
                              monitorTypes: Seq[String], tabType: String, range: Seq[Long])

  implicit val settingRead = Json.reads[EngineAuditSetting]
  implicit val auditParamRead = Json.reads[EngineAuditParam]

  def getStartEnd(range: Seq[Long]) = (new DateTime(range(0)), new DateTime(range(1)))

  def audit(recordDB: RecordDB, monitorTypeDB: MonitorTypeDB)(param: EngineAuditParam): Future[Seq[Int]] = {
    val (start, end) = getStartEnd(param.range)
    val tabType = TableType.withName(param.tabType)
    val colName = TableType.mapCollection(tabType)

    import scala.collection.mutable.ListBuffer
    val updatedRecordList = ListBuffer.empty[RecordList]

    def checkTurn(lastMtMap: Map[String, MtRecord], mtMap: Map[String, MtRecord]): Option[String] =
      for {lastSpeedRecord <- lastMtMap.get(MonitorType.SPEED)
           speedRecord <- mtMap.get(MonitorType.SPEED)
           lastSpeed <- lastSpeedRecord.value
           speed <- speedRecord.value if speed - lastSpeed > param.setting.turnSpeedThreshold
           lastDirectionRecord <- lastMtMap.get(MonitorType.DIRECTION)
           directionRecord <- mtMap.get(MonitorType.DIRECTION)
           lastDirection <- lastDirectionRecord.value
           direction <- directionRecord.value if Math.abs(direction - lastDirection) > param.setting.turnDirectionThreshold
           } yield
        "T"

    def checkWindDirOffset(mtMap: Map[String, MtRecord]) =
      for {
        winDirOffsetRecord <- mtMap.get(MonitorType.WIND_DIRECTION_OFFSET)
        winDirOffset <- winDirOffsetRecord.value if winDirOffset >= param.setting.directionOffsetThreshold
      } yield
        "D"

    def checkWindSpeed(mtMap: Map[String, MtRecord]) =
      for {
        winSpeedRecord <- mtMap.get(MonitorType.WIN_SPEED)
        winSpeed <- winSpeedRecord.value if winSpeed >= param.setting.windSpeedThreshold
      } yield
        "H"

    def checkSailing(mtMap: Map[String, MtRecord]) =
      for {
        directionRecord <- mtMap.get(MonitorType.DIRECTION)
        direction <- directionRecord.value if direction >= 0.0001
      } yield
        true

    def auditMonitor(m: String): Future[Int] =
      for (recordLists <- recordDB.getRecordListFuture(colName)(start, end, Seq(m)))
        yield {
          monitorTypeDB.appendCalculatedMtRecord(recordLists, monitorTypeDB.calculatedMonitorTypes.map(_._id))

          var markCount = 0
          var tag = ""
          for (records <- recordLists.sliding(2)) {
            val lastMtMap = records.head.mtMap
            val mtMap = records.last.mtMap

            val sailing = checkSailing(mtMap).getOrElse(false)
            val t = checkTurn(lastMtMap, mtMap).getOrElse("")
            val d = checkWindDirOffset(mtMap).getOrElse("U")
            val h = checkWindSpeed(mtMap).getOrElse("L")

            def markRecordList(recordList: RecordList): Unit = {
              recordList.mtDataList.foreach(mtRecord => {
                if (param.monitorTypes.contains(mtRecord.mtName)) {
                  val tagInfo = MonitorStatus.getTagInfo(mtRecord.status)
                  tagInfo.statusType = StatusType.Auto
                  tagInfo.auditRule = Some(tag)
                  mtRecord.status = tagInfo.toString
                }
              })
              updatedRecordList.append(recordList)
            }

            def marked: Unit = {
              tag = s"$t$d$h"
              markCount = 6
              markRecordList(records.last)
            }


            if (markCount == 0) {
              if (sailing)
                (t, d, h) match {
                  case ("T", "D", "L") =>
                    marked
                  case ("T", "D", "H") =>
                    marked
                  case ("T", "U", "L") =>
                    marked
                  case ("T", "U", "H") =>
                    marked
                  case ("", "D", "L") =>
                    tag = s"$t$d$h"
                    markCount = 0
                    markRecordList(records.last)
                  case _ =>
                }
            } else {
              if (sailing) {
                markCount = markCount - 1
                markRecordList(records.last)
              }
            }
          }

          // Update DB
          recordDB.upsertManyRecords(colName)(updatedRecordList)
          recordLists.length
        }

    val ret = param.monitors.map(auditMonitor)
    Future.sequence(ret)
  }

  case class RevertEngineAuditParam(monitors: Seq[String], tabType: String, monitorTypes: Seq[String], range: Seq[Long])

  implicit val revertRead = Json.reads[RevertEngineAuditParam]

  def revert(recordDB: RecordDB)(param: RevertEngineAuditParam) = {
    val (start, end) = getStartEnd(param.range)
    val colName = TableType.mapCollection(TableType.withName(param.tabType))
    val recordListF = recordDB.getRecordListFuture(colName)(startTime = start, endTime = end,
      monitors = param.monitors)


    import scala.collection.mutable.ListBuffer
    val updatedRecordList = ListBuffer.empty[RecordList]

    def revertHelper(recordList: RecordList) = {
      var altered = false
      recordList.mtDataList.foreach(mtRecord => {
        if (param.monitorTypes.contains(mtRecord.mtName) && !mtRecord.status.startsWith("0")) {
          mtRecord.status = MonitorStatus.revert(mtRecord.status)
          altered = true
        }
      })
      if (altered)
        updatedRecordList.append(recordList)
    }

    val ret =
      for (recordLists <- recordListF) yield {
        recordLists.foreach(recordList => {
          revertHelper(recordList)
        })

        for (_ <- recordDB.upsertManyRecords(colName)(updatedRecordList)) yield
          updatedRecordList.length
      }
    ret.flatMap(x => x)
  }
}
