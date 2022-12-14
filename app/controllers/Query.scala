package controllers

import com.github.nscala_time.time.Imports
import com.github.nscala_time.time.Imports._
import controllers.Highchart._
import models.ModelHelper.windAvg
import models._
import play.api._
import play.api.libs.json._
import play.api.mvc._

import java.nio.file.Files
import javax.inject._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Stat(avg: Option[Double],
                min: Option[Double],
                max: Option[Double],
                count: Int,
                total: Int,
                overCount: Int,
                valid: Boolean) {
  val effectPercent = {
    if (total > 0)
      Some(count.toDouble * 100 / total)
    else
      None
  }

  val isEffective = valid

  val overPercent = {
    if (count > 0)
      Some(overCount.toDouble * 100 / total)
    else
      None
  }
}

case class CellData(v: String, cellClassName: Seq[String], status: Option[String] = None)

case class RowData(date: Long, cellData: Seq[CellData])

case class DataTab(columnNames: Seq[String], rows: Seq[RowData])

case class ManualAuditParam(reason: String, updateList: Seq[UpdateRecordParam])

case class UpdateRecordParam(time: Long, monitor: String, mt: String, status: String)

@Singleton
class Query @Inject()(recordOp: RecordDB, monitorTypeOp: MonitorTypeDB, monitorOp: MonitorDB,
                      instrumentStatusOp: InstrumentStatusDB, instrumentOp: InstrumentDB,
                      alarmOp: AlarmDB, calibrationOp: CalibrationDB,
                      manualAuditLogOp: ManualAuditLogDB, excelUtility: ExcelUtility,
                      instrumentStatusTypeDB: InstrumentStatusTypeDB,
                      groupDB: GroupDB,
                      configuration: Configuration) extends Controller {

  implicit val cdWrite = Json.writes[CellData]
  implicit val rdWrite = Json.writes[RowData]
  implicit val dtWrite = Json.writes[DataTab]

  def getPeriodCount(start: DateTime, endTime: DateTime, p: Period) = {
    var count = 0
    var current = start
    while (current < endTime) {
      count += 1
      current += p
    }

    count
  }

  def getPeriodStatReportMap(recordListMap: Map[String, Seq[Record]], period: Period,
                             statusFilter: MonitorStatusFilter.Value = MonitorStatusFilter.ValidData)
                            (start: DateTime, end: DateTime):
  Map[String, Map[Imports.DateTime, Stat]] = {
    val mTypes = recordListMap.keys.toList
    if (mTypes.contains(MonitorType.WIN_DIRECTION)) {
      if (!mTypes.contains(MonitorType.WIN_SPEED))
        throw new Exception("?????????????????????????????????")
    }

    if (period.getHours == 1) {
      throw new Exception("???????????????Stat??????")
    }

    def periodSlice(recordList: Seq[Record], period_start: DateTime, period_end: DateTime) = {
      recordList.dropWhile {
        _.time < period_start
      }.takeWhile {
        _.time < period_end
      }
    }

    def getPeriodStat(records: Seq[Record], mt: String, period_start: DateTime, minimumValidCount: Int): Stat = {
      val values = records.filter(rec => MonitorStatusFilter.isMatched(statusFilter, rec.status))
        .flatMap(x => x.value)
      if (values.length == 0)
        Stat(None, None, None, 0, 0, 0, false)
      else {
        val min = values.min
        val max = values.max
        val sum = values.sum
        val count = values.length
        val total = new Duration(period_start, period_start + period).getStandardHours.toInt
        val overCount = if (monitorTypeOp.map(mt).std_law.isDefined) {
          values.count {
            _ > monitorTypeOp.map(mt).std_law.get
          }
        } else
          0

        val avg = if (mt == MonitorType.WIN_DIRECTION) {
          val windDir = records
          val windSpeed = periodSlice(recordListMap(MonitorType.WIN_SPEED), period_start, period_start + period)
          windAvg(windSpeed, windDir)
        } else {
          if (count != 0)
            Some(sum / count)
          else
            None
        }
        Stat(
          avg = avg,
          min = Some(min),
          max = Some(max),
          total = total,
          count = count,
          overCount = overCount,
          valid = count >= minimumValidCount)
      }
    }

    val validMinimumCount = if (period == 1.day)
      16
    else if (period == 1.month)
      20
    else
      throw new Exception(s"unknown minimumValidCount for ${period}")

    val pairs = {
      for {
        mt <- mTypes
      } yield {
        val timePairs =
          for {
            period_start <- getPeriods(start, end, period)
            records = periodSlice(recordListMap(mt), period_start, period_start + period)
          } yield {
            period_start -> getPeriodStat(records, mt, period_start, validMinimumCount)
          }
        mt -> Map(timePairs: _*)
      }
    }

    Map(pairs: _*)
  }

  import models.ModelHelper._

  def scatterChart(monitorStr: String, monitorTypeStr: String, tabTypeStr: String, statusFilterStr: String,
                   startNum: Long, endNum: Long) = Security.Authenticated.async {
    implicit request =>
      val userInfo = request.user
      val monitors = monitorStr.split(':')
      val monitorTypeStrArray = monitorTypeStr.split(':')
      val monitorTypes = monitorTypeStrArray
      val statusFilter = MonitorStatusFilter.withName(statusFilterStr)
      val tabType = TableType.withName(tabTypeStr)
      val (start, _end) =
        if (tabType == TableType.hour)
          (new DateTime(startNum).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0),
            new DateTime(endNum).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0))
        else
          (new DateTime(startNum).withSecondOfMinute(0).withMillisOfSecond(0),
            new DateTime(endNum).withSecondOfMinute(0).withMillisOfSecond(0))

      assert(monitorTypes.length == 2)

      val retFF =
        for (groupOpt <- groupDB.getGroupByIdAsync(userInfo.group)) yield {
          val group = groupOpt.get
          var end =
            if (_end >= DateTime.now().minusHours(group.delayHour.getOrElse(0)))
              DateTime.now().minusHours(group.delayHour.getOrElse(0))
            else
              _end

          for (chart <- compareChartHelper(monitors, monitorTypes, tabType, start, end)(statusFilter)) yield
            Results.Ok(Json.toJson(chart))
        }
      retFF.flatMap(x => x)
  }


  def historyTrendChart(monitorStr: String, monitorTypeStr: String, reportUnitStr: String, statusFilterStr: String,
                        startNum: Long, endNum: Long, outputTypeStr: String) = Security.Authenticated.async {
    implicit request =>
      val userInfo = request.user
      val monitors = monitorStr.split(':')
      val monitorTypeStrArray = monitorTypeStr.split(':')
      val monitorTypes = monitorTypeStrArray
      val reportUnit = ReportUnit.withName(reportUnitStr)
      val statusFilter = MonitorStatusFilter.withName(statusFilterStr)

      for (groupOpt <- groupDB.getGroupByIdAsync(userInfo.group)) yield {
        val group = groupOpt.get
        var endDateTime = new DateTime(endNum)

        if (endDateTime >= DateTime.now().minusHours(group.delayHour.getOrElse(0)))
          endDateTime = DateTime.now().minusHours(group.delayHour.getOrElse(0))

        val (tabType, start, end) =
          if (reportUnit.id <= ReportUnit.Hour.id) {
            if (reportUnit == ReportUnit.Hour)
              (TableType.hour, new DateTime(startNum).withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0),
                endDateTime.withMinuteOfHour(0).withSecondOfMinute(0).withMillisOfSecond(0))
            else if (reportUnit == ReportUnit.Sec)
              (TableType.second, new DateTime(startNum).withMillisOfSecond(0), endDateTime.withMillisOfSecond(0))
            else
              (TableType.min, new DateTime(startNum).withSecondOfMinute(0).withMillisOfSecond(0),
                endDateTime.withSecondOfMinute(0).withMillisOfSecond(0))
          } else
            (TableType.hour, new DateTime(startNum).withMillisOfDay(0), endDateTime.withMillisOfDay(0))


        val outputType = OutputType.withName(outputTypeStr)
        val chart = trendHelper(monitors, monitorTypes, tabType,
          reportUnit, start, end, LoggerConfig.config.trendShowActual)(statusFilter)

        if (outputType == OutputType.excel) {
          import java.nio.file.Files
          val exportMonitorTypes = List.fill(monitors.length)(monitorTypes).flatten
          val excelFile = excelUtility.exportChartData(chart, exportMonitorTypes.toArray, true)
          val downloadFileName =
            if (chart.downloadFileName.isDefined)
              chart.downloadFileName.get
            else
              chart.title("text")

          Ok.sendFile(excelFile, fileName = _ =>
            s"${downloadFileName}.xlsx",
            onClose = () => {
              Files.deleteIfExists(excelFile.toPath())
            })
        } else {
          Results.Ok(Json.toJson(chart))
        }
      }
  }

  def trendHelper(monitors: Seq[String], monitorTypes: Seq[String], tabType: TableType.Value,
                  reportUnit: ReportUnit.Value, start: DateTime, end: DateTime, showActual: Boolean)(statusFilter: MonitorStatusFilter.Value) = {
    val period: Period =
      reportUnit match {
        case ReportUnit.Sec =>
          1.second
        case ReportUnit.Min =>
          1.minute
        case ReportUnit.SixMin =>
          6.minute
        case ReportUnit.TenMin =>
          10.minute
        case ReportUnit.FifteenMin =>
          15.minute
        case ReportUnit.Hour =>
          1.hour
        case ReportUnit.Day =>
          1.day
        case ReportUnit.Month =>
          1.month
        case ReportUnit.Quarter =>
          3.month
        case ReportUnit.Year =>
          1.year
      }

    val timeSeq = getPeriods(start, end, period)

    val downloadFileName = {
      val startStr = start.toString("YYMMdd")
      val endStr = end.toString("YYMMdd")
      val monitorNames = monitors.map(monitorOp.map(_).desc).mkString
      val mtNames = monitorTypes.map(monitorTypeOp.map(_).desp).mkString
      s"${monitorNames}${mtNames}${startStr}_${endStr}"
    }

    val title =
      reportUnit match {
        case ReportUnit.Sec =>
          s"????????? (${start.toString("YYYY???MM???dd??? HH:mm")}~${end.toString("YYYY???MM???dd??? HH:mm")})"
        case ReportUnit.Min =>
          s"????????? (${start.toString("YYYY???MM???dd??? HH:mm")}~${end.toString("YYYY???MM???dd??? HH:mm")})"
        case ReportUnit.SixMin =>
          s"????????? (${start.toString("YYYY???MM???dd??? HH:mm")}~${end.toString("YYYY???MM???dd??? HH:mm")})"
        case ReportUnit.TenMin =>
          s"????????? (${start.toString("YYYY???MM???dd??? HH:mm")}~${end.toString("YYYY???MM???dd??? HH:mm")})"
        case ReportUnit.FifteenMin =>
          s"????????? (${start.toString("YYYY???MM???dd??? HH:mm")}~${end.toString("YYYY???MM???dd??? HH:mm")})"
        case ReportUnit.Hour =>
          s"????????? (${start.toString("YYYY???MM???dd??? HH:mm")}~${end.toString("YYYY???MM???dd??? HH:mm")})"
        case ReportUnit.Day =>
          s"????????? (${start.toString("YYYY???MM???dd???")}~${end.toString("YYYY???MM???dd???")})"
        case ReportUnit.Month =>
          s"????????? (${start.toString("YYYY???MM???")}~${end.toString("YYYY???MM???dd???")})"
        case ReportUnit.Quarter =>
          s"????????? (${start.toString("YYYY???MM???")}~${end.toString("YYYY???MM???dd???")})"
        case ReportUnit.Year =>
          s"????????? (${start.toString("YYYY???")}~${end.toString("YYYY???")})"
      }

    def getAxisLines(mt: String) = {
      val mtCase = monitorTypeOp.map(mt)
      val std_law_line =
        if (mtCase.std_law.isEmpty)
          None
        else
          Some(AxisLine("#FF0000", 2, mtCase.std_law.get, Some(AxisLineLabel("right", "?????????"))))

      val lines = Seq(std_law_line, None).filter {
        _.isDefined
      }.map {
        _.get
      }
      if (lines.length > 0)
        Some(lines)
      else
        None
    }

    val yAxisGroup: Map[String, Seq[(String, Option[Seq[AxisLine]])]] = monitorTypes.map(mt => {
      (monitorTypeOp.map(mt).unit, getAxisLines(mt))
    }).groupBy(_._1)
    val yAxisGroupMap = yAxisGroup map {
      kv =>
        val lines: Seq[AxisLine] = kv._2.map(_._2).flatten.flatten
        if (lines.nonEmpty)
          kv._1 -> YAxis(None, AxisTitle(Some(Some(s"${kv._1}"))), Some(lines))
        else
          kv._1 -> YAxis(None, AxisTitle(Some(Some(s"${kv._1}"))), None)
    }
    val yAxisIndexList = yAxisGroupMap.toList.zipWithIndex
    val yAxisUnitMap = yAxisIndexList.map(kv => kv._1._1 -> kv._2).toMap
    val yAxisList = yAxisIndexList.map(_._1._2)

    def getSeries() = {

      val monitorReportPairs =
        for {
          monitor <- monitors
        } yield {
          monitor -> getPeriodReportMap(monitor, monitorTypes, tabType, period, statusFilter)(start, end)
        }

      val monitorReportMap = monitorReportPairs.toMap
      for {
        m <- monitors
        mt <- monitorTypes
        valueMap = monitorReportMap(m)(mt)
      } yield {
        val timeData =
          if (showActual) {
            timeSeq.map { time =>
              if (valueMap.contains(time))
                (time.getMillis, Some(valueMap(time)))
              else
                (time.getMillis, None)
            }
          } else {
            for (time <- valueMap.keys.toList.sorted) yield {
              (time.getMillis, Some(valueMap(time)))
            }
          }
        val timeValues = timeData.map {
          t =>
            val time = t._1
            val valueOpt = for (x <- t._2) yield x._1
            (time, valueOpt.getOrElse(None))
        }
        val timeStatus = timeData.map {
          t =>
            val statusOpt = for (x <- t._2) yield x._2
            statusOpt.getOrElse(None)
        }
        seqData(name = s"${monitorOp.map(m).desc}_${monitorTypeOp.map(mt).desp}",
          data = timeValues, yAxis = yAxisUnitMap(monitorTypeOp.map(mt).unit),
          tooltip = Tooltip(monitorTypeOp.map(mt).prec), statusList = timeStatus)
      }
    }

    val series = getSeries()

    val xAxis = {
      val duration = new Duration(start, end)
      if (duration.getStandardDays > 2)
        XAxis(None, gridLineWidth = Some(1), None)
      else
        XAxis(None)
    }

    val chart =
      if (monitorTypes.length == 1) {
        val mt = monitorTypes(0)
        val mtCase = monitorTypeOp.map(monitorTypes(0))

        HighchartData(
          Map("type" -> "line"),
          Map("text" -> title),
          xAxis,

          Seq(YAxis(None, AxisTitle(Some(Some(s"${mtCase.desp} (${mtCase.unit})"))), getAxisLines(mt))),
          series,
          Some(downloadFileName))
      } else {
        HighchartData(
          Map("type" -> "line"),
          Map("text" -> title),
          xAxis,
          yAxisList,
          series,
          Some(downloadFileName))
      }

    chart
  }

  def getPeriodReportMap(monitor: String, mtList: Seq[String],
                         tabType: TableType.Value, period: Period,
                         statusFilter: MonitorStatusFilter.Value = MonitorStatusFilter.ValidData)
                        (start: DateTime, end: DateTime): Map[String, Map[DateTime, (Option[Double], Option[String])]] = {
    val mtRecordListMap = recordOp.getRecordMap(TableType.mapCollection(tabType))(monitor, mtList, start, end)

    val mtRecordPairs =
      for (mt <- mtList) yield {
        val recordList = mtRecordListMap(mt)

        def periodSlice(period_start: DateTime, period_end: DateTime) = {
          recordList.dropWhile {
            _.time < period_start
          }.takeWhile {
            _.time < period_end
          }
        }

        val pairs =
          if ((tabType == TableType.hour && period.getHours == 1) || (tabType == TableType.min && period.getMinutes == 1)) {
            recordList.filter { r => MonitorStatusFilter.isMatched(statusFilter, r.status) }.map { r => r.time -> (r.value, Some(r.status)) }
          } else {
            for {
              period_start <- getPeriods(start, end, period)
              rawRecords = periodSlice(period_start, period_start + period) if rawRecords.length > 0
              records = rawRecords.filter(r => MonitorStatusFilter.isMatched(statusFilter, r.status))
            } yield {
              if (mt == MonitorType.WIN_DIRECTION) {
                val windDir = records
                val windSpeed = recordOp.getRecordMap(TableType.mapCollection(tabType))(monitor, List(MonitorType.WIN_SPEED), period_start, period_start + period)(MonitorType.WIN_SPEED)
                period_start -> (windAvg(windSpeed, windDir), None)
              } else {
                val values = records.flatMap { r => r.value }
                if (values.nonEmpty)
                  period_start -> (Some(values.sum / values.length), None)
                else
                  period_start -> (None, None)
              }
            }
          }
        mt -> Map(pairs: _*)
      }
    mtRecordPairs.toMap
  }

  def getPeriods(start: DateTime, endTime: DateTime, d: Period): List[DateTime] = {
    import scala.collection.mutable.ListBuffer

    val buf = ListBuffer[DateTime]()
    var current = start
    while (current < endTime) {
      buf.append(current)
      current += d
    }

    buf.toList
  }

  def compareChartHelper(monitors: Seq[String], monitorTypes: Seq[String], tabType: TableType.Value,
                         start: DateTime, end: DateTime)(statusFilter: MonitorStatusFilter.Value): Future[ScatterChart] = {

    val downloadFileName = {
      val startName = start.toString("YYMMdd")
      val mtNames = monitorTypes.map {
        monitorTypeOp.map(_).desp
      }
      startName + mtNames.mkString
    }

    val mtName = monitorTypes.map(monitorTypeOp.map(_).desp).mkString(" vs ")
    val title =
      tabType match {
        case TableType.min =>
          s"$mtName ????????? (${start.toString("YYYY???MM???dd??? HH:mm")}~${end.toString("YYYY???MM???dd??? HH:mm")})"
        case TableType.hour =>
          s"$mtName ????????? (${start.toString("YYYY???MM???dd??? HH:mm")}~${end.toString("YYYY???MM???dd??? HH:mm")})"
      }

    def getAxisLines(mt: String) = {
      val mtCase = monitorTypeOp.map(mt)
      val std_law_line =
        if (mtCase.std_law.isEmpty)
          None
        else
          Some(AxisLine("#FF0000", 2, mtCase.std_law.get, Some(AxisLineLabel("right", "?????????"))))

      val lines = Seq(std_law_line, None).filter {
        _.isDefined
      }.map {
        _.get
      }
      if (lines.length > 0)
        Some(lines)
      else
        None
    }

    val yAxisGroup: Map[String, Seq[(String, Option[Seq[AxisLine]])]] = monitorTypes.map(mt => {
      (monitorTypeOp.map(mt).unit, getAxisLines(mt))
    }).groupBy(_._1)
    val yAxisGroupMap = yAxisGroup map {
      kv =>
        val lines: Seq[AxisLine] = kv._2.map(_._2).flatten.flatten
        if (lines.nonEmpty)
          kv._1 -> YAxis(None, AxisTitle(Some(Some(s"${kv._1}"))), Some(lines))
        else
          kv._1 -> YAxis(None, AxisTitle(Some(Some(s"${kv._1}"))), None)
    }
    val yAxisIndexList = yAxisGroupMap.toList.zipWithIndex

    def getSeriesFuture() = {
      val seqFuture = monitors.map(m => {
        for (records <- recordOp.getRecordListFuture(TableType.mapCollection(tabType))(start, end, Seq(m))) yield {
          monitorTypeOp.appendCalculatedMtRecord(records, monitorTypes)
          val data = records.flatMap(rec => {
            for {mt1 <- rec.mtMap.get(monitorTypes(0)) if MonitorStatusFilter.isMatched(statusFilter, mt1.status)
                 mt2 <- rec.mtMap.get(monitorTypes(1)) if MonitorStatusFilter.isMatched(statusFilter, mt2.status)
                 mt1Value <- mt1.value
                 mt2Value <- mt2.value} yield Seq(mt1Value, mt2Value)
          })
          ScatterSeries(name = s"${monitorOp.map(m).desc}", data = data)
        }
      })
      for (ret <- Future.sequence(seqFuture)) yield {
        val combinedData = ret.flatMap(series => series.data)
        ret :+ ScatterSeries(name = monitors.map(m => s"${monitorOp.map(m).desc}").mkString("+"), data = combinedData)
      }
    }

    val mt1 = monitorTypeOp.map(monitorTypes(0))
    val mt2 = monitorTypeOp.map(monitorTypes(1))

    for (series <- getSeriesFuture()) yield
      ScatterChart(Map("type" -> "scatter", "zoomType" -> "xy"),
        Map("text" -> title),
        ScatterAxis(Title(true, s"${mt1.desp}(${mt1.unit})"), getAxisLines(monitorTypes(0))),
        ScatterAxis(Title(true, s"${mt2.desp}(${mt2.unit})"), getAxisLines(monitorTypes(1))),
        series, Some(downloadFileName))
  }

  def historyData(monitorStr: String, monitorTypeStr: String, tabTypeStr: String,
                  startNum: Long, endNum: Long) = Security.Authenticated.async {
    implicit request =>
      val monitors = monitorStr.split(":")
      val monitorTypes = monitorTypeStr.split(':')
      val tabType = TableType.withName(tabTypeStr)
      val userInfo = request.user
      val ret =
        for (groupOpt <- groupDB.getGroupByIdAsync(userInfo.group)) yield {
          val group = groupOpt.get
          val startDateTime = new DateTime(startNum)
          var endDateTime = new DateTime(endNum)

          if (endDateTime >= DateTime.now().minusHours(group.delayHour.getOrElse(0)))
            endDateTime = DateTime.now().minusHours(group.delayHour.getOrElse(0))

          val (start, end) =
            if (tabType == TableType.hour) {
              (startDateTime.withMinuteOfHour(0), endDateTime.withMinute(0) + 1.hour)
            } else {
              (startDateTime, endDateTime)
            }

          val resultFuture = recordOp.getRecordListFuture(TableType.mapCollection(tabType))(start, end, monitors)
          val emptyCell = CellData("-", Seq.empty[String])
          for (recordLists <- resultFuture) yield {
            import scala.collection.mutable.Map
            monitorTypeOp.appendCalculatedMtRecord(recordLists, monitorTypes)

            val timeMtMonitorMap = Map.empty[DateTime, Map[String, Map[String, CellData]]]
            recordLists foreach {
              r =>
                val stripedTime = new DateTime(r._id.time).withSecondOfMinute(0).withMillisOfSecond(0)
                val mtMonitorMap = timeMtMonitorMap.getOrElseUpdate(stripedTime, Map.empty[String, Map[String, CellData]])
                for (mt <- monitorTypes.toSeq) {
                  val monitorMap = mtMonitorMap.getOrElseUpdate(mt, Map.empty[String, CellData])
                  val cellData = if (r.mtMap.contains(mt)) {
                    val mtRecord = r.mtMap(mt)
                    CellData(monitorTypeOp.format(mt, mtRecord.value),
                      monitorTypeOp.getCssClassStr(mtRecord), Some(mtRecord.status))
                  } else
                    emptyCell

                  monitorMap.update(r._id.monitor, cellData)
                }
            }
            val timeList = timeMtMonitorMap.keys.toList.sorted
            val timeRows: Seq[RowData] = for (time <- timeList) yield {
              val mtMonitorMap = timeMtMonitorMap(time)
              var cellDataList = Seq.empty[CellData]
              for {
                mt <- monitorTypes
                m <- monitors
              } {
                val monitorMap = mtMonitorMap(mt)
                if (monitorMap.contains(m))
                  cellDataList = cellDataList :+ (mtMonitorMap(mt)(m))
                else
                  cellDataList = cellDataList :+ (emptyCell)
              }
              RowData(time.getMillis, cellDataList)
            }

            val columnNames = monitorTypes.toSeq map {
              monitorTypeOp.map(_).desp
            }
            Ok(Json.toJson(DataTab(columnNames, timeRows)))
          }
        }

      ret.flatMap(x => x)
  }

  def historyReport(monitorTypeStr: String, tabTypeStr: String,
                    startNum: Long, endNum: Long) = Security.Authenticated.async {
    implicit request =>

      val monitorTypes = monitorTypeStr.split(':')
      val tabType = TableType.withName(tabTypeStr)
      val (start, end) =
        if (tabType == TableType.hour) {
          val orignal_start = new DateTime(startNum)
          val orignal_end = new DateTime(endNum)

          (orignal_start.withMinuteOfHour(0), orignal_end.withMinute(0) + 1.hour)
        } else {
          val timeStart = new DateTime(startNum)
          val timeEnd = new DateTime(endNum)
          val timeDuration = new Duration(timeStart, timeEnd)
          tabType match {
            case TableType.min =>
              if (timeDuration.getStandardMinutes > 60 * 12)
                (timeStart, timeStart + 12.hour)
              else
                (timeStart, timeEnd)
            case TableType.second =>
              if (timeDuration.getStandardSeconds > 60 * 60)
                (timeStart, timeStart + 1.hour)
              else
                (timeStart, timeEnd)
          }
        }
      val timeList = tabType match {
        case TableType.hour =>
          getPeriods(start, end, 1.hour)
        case TableType.min =>
          getPeriods(start, end, 1.minute)
        case TableType.second =>
          getPeriods(start, end, 1.second)
      }

      val f = recordOp.getRecordListFuture(TableType.mapCollection(tabType))(start, end)

      for (recordList <- f) yield
        Ok(Json.toJson(recordList))
  }


  def alarmReport(level: Int, startNum: Long, endNum: Long): Action[AnyContent] = Security.Authenticated.async {
    implicit request =>
      val userInfo = request.user
      val ret =
        for (groupOpt <- groupDB.getGroupByIdAsync(userInfo.group)) yield {
          val group = groupOpt.get
          val start = new DateTime(startNum)
          var end = new DateTime(endNum)

          if (end >= DateTime.now().minusHours(group.delayHour.getOrElse(0)))
            end = DateTime.now().minusHours(group.delayHour.getOrElse(0))

          for (report <- alarmOp.getAlarmsFuture(level, start, end)) yield {
            Ok(Json.toJson(report))
          }
        }
      ret.flatMap(x => x)
  }


  def monitorAlarmReport(monitorStr: String, level: Int, startNum: Long, endNum: Long): Action[AnyContent] =
    Security.Authenticated.async {
      implicit request =>
        val userInfo = request.user
        val monitors = monitorStr.split(":").toList
        val ret =
          for (groupOpt <- groupDB.getGroupByIdAsync(userInfo.group)) yield {
            val group = groupOpt.get
            val start = new DateTime(startNum)
            var end = new DateTime(endNum)

            if (end >= DateTime.now().minusHours(group.delayHour.getOrElse(0)))
              end = DateTime.now().minusHours(group.delayHour.getOrElse(0))

            for (report <- alarmOp.getMonitorAlarmsFuture(monitors, start.toDate, end.toDate)) yield
              Ok(Json.toJson(report.filter(ar=>ar.level >= level)))
          }
        ret.flatMap(x => x)
    }

  def getAlarms(src: String, level: Int, startNum: Long, endNum: Long) = Security.Authenticated.async {
    implicit request =>
      val userInfo = request.user
      val ret =
        for (groupOpt <- groupDB.getGroupByIdAsync(userInfo.group)) yield {
          val group = groupOpt.get
          val start = new DateTime(startNum)
          var end = new DateTime(endNum)

          if (end >= DateTime.now().minusHours(group.delayHour.getOrElse(0)))
            end = DateTime.now().minusHours(group.delayHour.getOrElse(0))

          for (report <- alarmOp.getAlarmsFuture(src, level, start, end)) yield {
            Ok(Json.toJson(report))
          }
        }
      ret.flatMap(x => x)
  }

  def instrumentStatusReport(id: String, startNum: Long, endNum: Long) =
    Security.Authenticated.async {
      val (start, end) = (new DateTime(startNum).withMillisOfDay(0),
        new DateTime(endNum).withMillisOfDay(0))

      for (report <- instrumentStatusOp.queryAsync(id, start, end + 1.day)) yield {
        val keyList: Seq[String] = if (report.isEmpty)
          List.empty[String]
        else
          report.map {
            _.statusList
          }.maxBy {
            _.length
          }.map {
            _.key
          }

        val reportMap = for {
          record <- report
          time = record.time
        } yield {
          (time, record.statusList.map { s => (s.key -> s.value) }.toMap)
        }

        val statusTypeMap = instrumentOp.getStatusTypeMap(id)

        val columnNames: Seq[String] = keyList.map(statusTypeMap).map(_.desc)
        val rows = for (report <- reportMap) yield {
          val cellData = for (key <- keyList) yield {
            val instrumentStatusType = statusTypeMap(key)
            if (report._2.contains(key))
              CellData(instrumentStatusOp.formatValue(report._2(key), instrumentStatusType.prec.getOrElse(2)), Seq.empty[String])
            else
              CellData("-", Seq.empty[String])
          }
          RowData(report._1.getTime, cellData)
        }

        implicit val write = Json.writes[InstrumentReport]
        Ok(Json.toJson(InstrumentReport(columnNames, rows)))
      }
    }


  //This is for central server
  def monitorInstrumentStatusReport(monitor: String, id: String, startNum: Long, endNum: Long) =
    Security.Authenticated.async {
      val (start, end) = (new DateTime(startNum).withMillisOfDay(0),
        new DateTime(endNum).withMillisOfDay(0))


      for {report <- instrumentStatusOp.queryMonitorAsync(monitor, id, start, end + 1.day)
           statusTypeList <- instrumentStatusTypeDB.getAllInstrumentStatusTypeListAsync(monitor)
           } yield {
        val keyList: Seq[String] = if (report.isEmpty)
          List.empty[String]
        else
          report.map {
            _.statusList
          }.maxBy {
            _.length
          }.map {
            _.key
          }

        val reportMap = for {
          record <- report
          time = record.time
        } yield {
          (time, record.statusList.map { s => (s.key -> s.value) }.toMap)
        }

        val instrumentStatusTypeMap = statusTypeList.find(istMap => istMap.instrumentId == id).get
        val statusTypeMap = instrumentStatusTypeMap.statusTypeSeq.map(pair => pair.key -> pair).toMap
        val columnNames: Seq[String] = keyList.map(statusTypeMap).map(_.desc)
        val rows = for (report <- reportMap) yield {
          val cellData = for (key <- keyList) yield {
            val instrumentStatusType = statusTypeMap(key)
            if (report._2.contains(key))
              CellData(instrumentStatusOp.formatValue(report._2(key), instrumentStatusType.prec.getOrElse(2)), Seq.empty[String])
            else
              CellData("-", Seq.empty[String])
          }
          RowData(report._1.getTime, cellData)
        }

        implicit val write = Json.writes[InstrumentReport]
        Ok(Json.toJson(InstrumentReport(columnNames, rows)))
      }
    }

  implicit val write = Json.writes[InstrumentReport]

  def recordList(mtStr: String, startLong: Long, endLong: Long) = Security.Authenticated {
    val monitorType = (mtStr)
    implicit val w = Json.writes[Record]
    val (start, end) = (new DateTime(startLong), new DateTime(endLong))

    val recordMap = recordOp.getRecordMap(recordOp.HourCollection)(Monitor.activeId, List(monitorType), start, end)
    Ok(Json.toJson(recordMap(monitorType)))
  }

  def updateRecord(tabTypeStr: String) = Security.Authenticated(BodyParsers.parse.json) {
    implicit request =>
      val user = request.user
      implicit val read = Json.reads[UpdateRecordParam]
      implicit val maParamRead = Json.reads[ManualAuditParam]
      val result = request.body.validate[ManualAuditParam]
      val tabType = TableType.withName(tabTypeStr)
      result.fold(
        err => {
          Logger.error(JsError.toJson(err).toString())
          BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(err).toString()))
        },
        maParam => {
          for (param <- maParam.updateList) {
            recordOp.updateRecordStatus(dt = param.time, monitor = param.monitor, mt = param.mt, status = param.status)(TableType.mapCollection(tabType))
            val log = ManualAuditLog(new DateTime(param.time).toDate, monitor = param.monitor, mt = param.mt, modifiedTime = DateTime.now(),
              operator = user.name, changedStatus = param.status, reason = maParam.reason)
            manualAuditLogOp.upsertLog(log)
          }
        })
      Ok(Json.obj("ok" -> true))
  }

  def manualAuditHistoryReport(start: Long, end: Long) = Security.Authenticated.async {
    val startTime = new DateTime(start)
    val endTime = new DateTime(end)
    implicit val w = Json.writes[ManualAuditLog]
    val logFuture = manualAuditLogOp.queryLog2(startTime, endTime)
    val resultF =
      for {
        logList <- logFuture
      } yield {
        Ok(Json.toJson(logList))
      }

    resultF
  }

  // FIXME Bypass security check
  def hourRecordList(start: Long, end: Long) = Action.async {
    implicit request =>

      val startTime = new DateTime(start)
      val endTime = new DateTime(end)
      val recordListF = recordOp.getRecordListFuture(recordOp.HourCollection)(startTime, endTime)
      for (recordList <- recordListF) yield {
        Ok(Json.toJson(recordList))
      }
  }

  // FIXME Bypass security check
  def minRecordList(start: Long, end: Long) = Action.async {
    implicit request =>
      val startTime = new DateTime(start)
      val endTime = new DateTime(end)
      val recordListF = recordOp.getRecordListFuture(recordOp.MinCollection)(startTime, endTime)

      for (recordList <- recordListF) yield {
        Ok(Json.toJson(recordList))
      }
  }

  def monitorCalibrationRecords(monitorStr: String, start: Long, end: Long, outputTypeStr: String) = Action.async {
    implicit request =>
      val monitors = monitorStr.split(":").toList
      val startTime = new DateTime(start)
      val endTime = new DateTime(end)
      val outputType = OutputType.withName(outputTypeStr)
      val recordListF = calibrationOp.monitorCalibrationReport(monitors, startTime, endTime)
      implicit val w = Json.writes[Calibration]
      for (records <- recordListF) yield {
        outputType match {
          case OutputType.html =>
            Ok(Json.toJson(records))
          case OutputType.excel =>
            val excelFile = excelUtility.calibrationReport(startTime, endTime, records)
            Ok.sendFile(excelFile, fileName = _ =>
              s"????????????.xlsx",
              onClose = () => {
                Files.deleteIfExists(excelFile.toPath())
              })
        }
      }
  }

  def windRoseReport(monitor: String, monitorType: String, tabTypeStr: String, nWay: Int, start: Long, end: Long) = Security.Authenticated.async {
    implicit request =>
      assert(nWay == 8 || nWay == 16 || nWay == 32)
      try {
        val mtCase = monitorTypeOp.map(monitorType)
        val levels = monitorTypeOp.map(monitorType).levels.getOrElse(Seq(1.0, 2.0, 3.0))
        val tableType = TableType.withName(tabTypeStr)
        val colName: String = tableType match {
          case TableType.hour =>
            recordOp.HourCollection
          case TableType.min =>
            recordOp.MinCollection
        }
        val f = recordOp.getWindRose(colName)(monitor, monitorType, new DateTime(start), new DateTime(end), levels.toList, nWay)
        f onFailure errorHandler
        for (windMap <- f) yield {
          assert(windMap.nonEmpty)

          val dirMap =
            Map(
              (0 -> "???"), (1 -> "?????????"), (2 -> "??????"), (3 -> "?????????"), (4 -> "???"),
              (5 -> "?????????"), (6 -> "??????"), (7 -> "?????????"), (8 -> "???"),
              (9 -> "?????????"), (10 -> "??????"), (11 -> "?????????"), (12 -> "???"),
              (13 -> "?????????"), (14 -> "??????"), (15 -> "?????????"))
          val dirStrSeq =
            for {
              dir <- 0 to nWay - 1
              dirKey = if (nWay == 8)
                dir * 2
              else if (nWay == 32) {
                if (dir % 2 == 0) {
                  dir / 2
                } else
                  dir + 16
              } else
                dir
            } yield dirMap.getOrElse(dirKey, "")

          var previous = 0d
          val concLevels = levels.flatMap { l =>
            if (l == levels.head && l == levels.last) {
              previous = l
              val s1 = "< %s%s".format(monitorTypeOp.format(monitorType, Some(l)), mtCase.unit)
              val s2 = "> %s%s".format(monitorTypeOp.format(monitorType, Some(l)), mtCase.unit)
              List(s1, s2)
            } else if (l == levels.head) {
              previous = l
              List("< %s%s".format(monitorTypeOp.format(monitorType, Some(l)), mtCase.unit))
            } else if (l == levels.last) {
              val s1 = "%s~%s%s".format(monitorTypeOp.format(monitorType, Some(previous)),
                monitorTypeOp.format(monitorType, Some(l)), mtCase.unit)
              val s2 = "> %s%s".format(monitorTypeOp.format(monitorType, Some(l)), mtCase.unit)
              List(s1, s2)
            } else {
              val s1 = "%s~%s%s".format(monitorTypeOp.format(monitorType, Some(previous)),
                monitorTypeOp.format(monitorType, Some(l)), mtCase.unit)
              val ret = List(s1)
              previous = l
              ret
            }
          }
          val series = for {
            level <- 0 to levels.length
          } yield {
            val data =
              for (dir <- 0 to nWay - 1)
                yield (dir.toLong, Some(windMap(dir)(level)))

            seqData(concLevels(level), data)
          }

          val title = ""
          val chart = HighchartData(
            scala.collection.immutable.Map("polar" -> "true", "type" -> "column"),
            scala.collection.immutable.Map("text" -> title),
            XAxis(Some(dirStrSeq)),
            Seq(YAxis(None, AxisTitle(Some(Some(""))), None)),
            series)
          Ok(Json.toJson(chart))
        }
      } catch {
        case ex: Throwable =>
          Logger.error(ex.getMessage, ex)
          Future {
            BadRequest("?????????")
          }
      }
  }

  case class InstrumentReport(columnNames: Seq[String], rows: Seq[RowData])
}