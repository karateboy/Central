package models

object StatusType extends Enumeration {
  val Internal = Value("0")
  val Auto = Value("A")
  val ManualInvalid = Value("M")
  val ManualValid = Value("m")

  def map: Map[StatusType.Value, String] = Map(Internal -> "系統",
    Auto -> "自動註記",
    ManualInvalid -> "人工註記:無效資料",
    ManualValid -> "人工註記:有效資料"
  )
}

case class MonitorStatus(_id: String, desp: String) {
  val info: TagInfo = MonitorStatus.getTagInfo(_id)
}

case class TagInfo(var statusType: StatusType.Value, var auditRule: Option[String], id: String) {
  override def toString: String = {
    if ((statusType != StatusType.Internal)
      && auditRule.isDefined)
      auditRule.get + id
    else
      statusType + id
  }
}

object MonitorStatus {
  val NormalStat = "010"
  val OverNormalStat = "011"
  val BelowNormalStat = "012"
  val ZeroCalibrationStat = "020"
  val SpanCalibrationStat = "021"
  val CalibrationDeviation = "022"
  val CalibrationResume = "026"
  val InvalidDataStat = "030"
  val MaintainStat = "031"
  val ExceedRangeStat = "032"

  def getTagInfo(tag: String): TagInfo = {
    val (t, id) = tag.splitAt(tag.length - 2)
    t match {
      case "0" =>
        TagInfo(StatusType.Internal, None, id)
      case "m" =>
        TagInfo(StatusType.ManualValid, Some(t), id)
      case "M" =>
        TagInfo(StatusType.ManualInvalid, Some(t), id)
      case _ =>
        if (t(0).isLetter)
          TagInfo(StatusType.Auto, Some(t), id)
        else
          throw new Exception("Unknown type:" + t)
    }
  }

  def getCssClassStr(tag: String, overInternal: Boolean = false, overLaw: Boolean = false): Seq[String] = {
    val info = getTagInfo(tag)
    val statClass =
      info.statusType match {
        case StatusType.Internal =>
          if (isValid(tag))
            ""
          else if (isCalibration(tag))
            "calibration_status"
          else if (isMaintenance(tag))
            "maintain_status"
          else
            "abnormal_status"

        case StatusType.Auto =>
          "auto_audit_status"
        case StatusType.ManualInvalid =>
          "manual_audit_status"
        case StatusType.ManualValid =>
          "manual_audit_status"
      }

    val fgClass =
      if (overLaw)
        "over_law_std"
      else if (overInternal)
        "over_internal_std"
      else
        "normal"

    if (statClass != "")
      Seq(statClass, fgClass)
    else
      Seq(fgClass)
  }

  def isValid(s: String): Boolean = {
    val tagInfo = getTagInfo(s)
    val VALID_STATS = List(NormalStat, OverNormalStat, BelowNormalStat).map(getTagInfo)

    tagInfo match {
      case TagInfo(StatusType.Internal, _, _) =>
        VALID_STATS.contains(getTagInfo(s))

      case TagInfo(StatusType.Auto, _, _) =>
        true

      case TagInfo(StatusType.ManualValid, _, _) =>
        true

      case TagInfo(StatusType.ManualInvalid, _, _) =>
        false

    }
  }

  def isCalibration(s: String): Boolean = {
    val CALIBRATION_STATS = List(ZeroCalibrationStat, SpanCalibrationStat,
      CalibrationDeviation, CalibrationResume).map(getTagInfo)

    CALIBRATION_STATS.contains(getTagInfo(s))
  }

  def isMaintenance(s: String): Boolean =
    getTagInfo(MaintainStat) == getTagInfo(s)

  def isManual(s: String): Boolean =
    getTagInfo(s).statusType == StatusType.ManualInvalid || getTagInfo(s).statusType == StatusType.ManualInvalid

  def isError(s: String): Boolean =
    !(isValid(s) || isCalibration(s) || isMaintenance(s))

  def revert(tag: String): String = {
    val tagInfo = getTagInfo(tag)
    s"0${tagInfo.id}"
  }
}

