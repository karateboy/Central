package models.sql

import com.mongodb.client.result.DeleteResult
import models.{Monitor, MonitorDB}
import scalikejdbc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@Singleton
class MonitorOp @Inject()(sqlServer: SqlServer, sysConfig: SysConfig, monitorTypeOp: MonitorTypeOp) extends MonitorDB {
  private val tabName = "monitor"

  override def deleteMonitor(_id: String): Future[DeleteResult] = Future {
    implicit val session: DBSession = AutoSession
    val ret =
      sql"""
         DELETE FROM [dbo].[monitor]
         Where [id] = ${_id}
         """.update().apply()
    DeleteResult.acknowledged(ret)
  }

  init()

  override def mList: List[Monitor] = {
    implicit val session: DBSession = ReadOnlyAutoSession
    sql"""
         Select *
         FROM [dbo].[monitor]
         """.map(mapper).list().apply()
  }

  private def mapper(rs: WrappedResultSet) = Monitor(rs.string("id"),
    rs.string("name"),
    rs.int("order"),
    rs.string("monitorTypes").split(","),
    rs.doubleOpt("lat"),
    rs.doubleOpt("lng"),
    rs.intOpt("epaId")
  )

  private def init()(implicit session: DBSession = AutoSession): Unit = {
    if (!sqlServer.getTables().contains(tabName)) {
      sql"""
          CREATE TABLE [dbo].[monitor](
	          [id] [nvarchar](50) NOT NULL,
	          [name] [nvarchar](256) NOT NULL,
	          [lat] [float] NULL,
	          [lng] [float] NULL,
            [epaId] [int] NULL,
            [order] [int] NOT NULL,
            [monitorTypes] [nvarchar](256) NOT NULL,
          CONSTRAINT [PK_monitor] PRIMARY KEY CLUSTERED
          (
	          [id] ASC
          )WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
          ) ON [PRIMARY]
           """.execute().apply()
      upsert(Monitor.defaultMonitor)
    }

    if (!sqlServer.getColumnNames(tabName).contains("epaId")) {
      sql"""
          Alter Table monitor
          Add [epaId] int NULL;
         """.execute().apply()
    }

    if (!sqlServer.getColumnNames(tabName).contains("order")) {
      sql"""
          Alter Table monitor
          Add [order] int NOT NULL default 0;
         """.execute().apply()
    }

    val defaultValue = SQLSyntax.createUnsafely(monitorTypeOp.activeMtvList.mkString(","))
    if (!sqlServer.getColumnNames(tabName).contains("monitorTypes"))
      sql"""
          Alter Table monitor
          Add [monitorTypes] [nvarchar](256) NOT NULL default '$defaultValue'
         """.execute().apply()

    refresh(sysConfig)
  }

  override def upsert(m: Monitor): Unit = {
    implicit val session: DBSession = AutoSession

    val mtStr = m.monitorTypes.mkString(",")
    sql"""
          UPDATE [dbo].[monitor]
            SET [name] = ${m.desc}
                ,[lat] = ${m.lat}
                ,[lng] = ${m.lng}
                ,[epaId] = ${m.epaId}
                ,[order] = ${m.order}
                ,[monitorTypes] = $mtStr
                Where [id] = ${m._id}
            IF(@@ROWCOUNT = 0)
            BEGIN
              INSERT INTO [dbo].[monitor]
              ([id]
                ,[name]
                ,[lat]
                ,[lng]
                ,[epaId]
                ,[order]
                ,[monitorTypes])
              VALUES
              (${m._id}
              ,${m.desc}
              ,${m.lat}
              ,${m.lng}
              ,${m.epaId}
              ,${m.order}
              ,$mtStr)
            END
         """.update().apply()
  }
}
