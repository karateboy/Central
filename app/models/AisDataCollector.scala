package models

import akka.actor.{Actor, ActorSystem, Props}
import play.api.libs.json.Json
import play.api.{Configuration, Logger}
import play.api.libs.ws.WSClient

import java.util.Date
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.duration.{FiniteDuration, HOURS, MILLISECONDS, MINUTES, SECONDS}
import scala.util.{Failure, Success}

case class PortConfig(monitor: String, apiKey: String)

case class AisDataCollectConfig(enable: Boolean, portConfigs: Seq[PortConfig])

object AisDataCollector {
  implicit val write = Json.writes[AisData]
  implicit val read = Json.reads[AisData]

  @volatile var enable = false

  private def getConfig(configuration: Configuration): Option[AisDataCollectConfig] = {
    for {config <- configuration.getConfig("aisCollector")
         enable <- config.getBoolean("enable")
         portConfigList <- config.getConfigList("ports")
         } yield {
      val portConfigs = portConfigList.asScala.flatMap(portConfig => {
        for (monitor <- portConfig.getString("monitor"); apiKey <- portConfig.getString("apiKey")) yield
          PortConfig(monitor = monitor, apiKey = apiKey)
      })
      AisDataCollectConfig(enable = enable, portConfigs = portConfigs.toSeq)
    }
  }

  def props(config: AisDataCollectConfig, monitorDB: MonitorDB, aisDB: AisDB, WSClient: WSClient) =
    Props(classOf[AisDataCollector], config, monitorDB, aisDB, WSClient)

  def start(configuration: Configuration, actorSystem: ActorSystem, monitorDB: MonitorDB, aisDB: AisDB, WSClient: WSClient) = {
    for (config <- getConfig(configuration) if config.enable) {
      enable = true
      actorSystem.actorOf(props(config, monitorDB, aisDB, WSClient), "aisCollector")
    }
  }

  case object CollectData

  case object CollectFullData
}

class AisDataCollector(config: AisDataCollectConfig, monitorDB: MonitorDB, aisDB: AisDB, WSClient: WSClient) extends Actor {
  Logger.info("AisDataCollector start.")

  import context.dispatcher
  import AisDataCollector._

  val timer = {
    import com.github.nscala_time.time.Imports._
    val next5 = DateTime.now().withSecondOfMinute(0).withMillisOfSecond(0).plusMinutes(5)
    val adjust5 = next5.withMinuteOfHour(next5.getMinuteOfHour % 5)
    val postMillis = new org.joda.time.Duration(DateTime.now, adjust5).getMillis
    context.system.scheduler.schedule(FiniteDuration(postMillis, MILLISECONDS), FiniteDuration(5, MINUTES), self, CollectData)
  }


  val fullTimer = context.system.scheduler.schedule(FiniteDuration(5, SECONDS), FiniteDuration(3, MINUTES), self, CollectFullData)
  config.portConfigs.foreach(port => monitorDB.ensure(port.monitor, Seq.empty[String]))

  override def receive: Receive = {
    case CollectData =>
      config.portConfigs.foreach(port => {
        val url = s"https://services.marinetraffic.com/api/exportvessels/${port.apiKey}?v=8&msgtype=simple&protocol=jsono"
        val f = WSClient
          .url(url)
          .get()

        f onComplete {
          case Success(res) =>
            if (res.status == 200) {
              aisDB.insertAisData(AisData(port.monitor, new Date(), res.json.toString(), aisDB.respSimpleType))
            }

          case Failure(exception) =>
            Logger.error(s"get $url failed", exception)
        }
      })

    case CollectFullData =>
      /*
      config.portConfigs.foreach(port => {
        val url = s"https://services.marinetraffic.com/api/exportvessels/${port.apiKey}?v=8&msgtype=full&protocol=jsono"
        val f = WSClient
          .url(url)
          .get()

        f onComplete {
          case Success(res) =>
            if (res.status == 200) {
              aisDB.insertAisData(AisData(port.monitor, new Date(), res.json.toString(), aisDB.respFullType))
            }

          case Failure(exception) =>
            Logger.error(s"get $url failed", exception)
        }
      })
      */
  }

  override def postStop(): Unit = {
    timer.cancel()
    fullTimer.cancel()
  }
}
