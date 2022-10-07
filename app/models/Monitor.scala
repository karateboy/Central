package models

case class Monitor(_id: String, desc: String, order: Int,
                   monitorTypes: Seq[String],
                   lat: Option[Double] = None, lng: Option[Double] = None,
                   epaId: Option[Int] = None)

object Monitor {
  @volatile var activeId = "me"
  @volatile private var order = 0
  val defaultMonitor = Monitor(activeId, "本站", 1, Seq.empty[String])

  def setActiveMonitorId(_id: String): Unit = {
    activeId = _id
  }

  def getOrder() = {
    order = order + 1
    order
  }
}

