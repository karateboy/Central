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

  def audit(recordDB: RecordDB, monitorTypeDB: MonitorTypeDB)(param: EngineAuditParam) = {
    val (start, end) = getStartEnd(param.range)

    val recordListF = recordDB.getRecordListFuture(recordDB.MinCollection)(startTime = start, endTime = end,
      monitors = param.monitors)
    val recordListWithCalculatedMt: Future[Seq[RecordList]] =
      for (recordLists <- recordListF) yield {
        monitorTypeDB.appendCalculatedMtRecord(recordLists, monitorTypeDB.calculatedMonitorTypes.map(_._id))
        recordLists
      }

    // Start audit
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

    for (recordLists <- recordListF) yield {
      recordLists.foreach(recordList => {
        revertHelper(recordList)
      })
      recordDB.upsertManyRecords(colName)(updatedRecordList)
      updatedRecordList.length
    }
  }
}
