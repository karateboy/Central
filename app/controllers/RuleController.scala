package controllers

import models.EngineAudit.{EngineAuditParam, RevertEngineAuditParam}
import models._
import play.api.Logger
import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc.{Action, AnyContent, BodyParsers, Controller}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RuleController @Inject()(spikeRuleOp: SpikeRuleDB, constantRuleOp: ConstantRuleDB,
                               variationRuleOp: VariationRuleDB,
                               recordDB: RecordDB, monitorTypeDB: MonitorTypeDB) extends Controller {

  def getSpikeRules(): Action[AnyContent] = Security.Authenticated.async {
    for (ret <- spikeRuleOp.getRules()) yield
      Ok(Json.toJson(ret))
  }

  def upsertSpikeRule(): Action[JsValue] = Security.Authenticated.async(BodyParsers.parse.json) {
    implicit request =>
      val ret = request.body.validate[SpikeRule]
      ret.fold(
        error => {
          Logger.error(JsError.toJson(error).toString())
          Future {
            BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error).toString()))
          }
        },
        rule => {
          for (_ <- spikeRuleOp.upsert(rule)) yield
            Ok(Json.obj("ok" -> true))
        })
  }

  def deleteSpikeRule(monitor: String, monitorType: String): Action[AnyContent] = Security.Authenticated.async {
    val id = SpikeRuleID(monitor, monitorType)
    for (_ <- spikeRuleOp.delete(id)) yield
      Ok(Json.obj("ok" -> true))
  }

  def getConstantRules(): Action[AnyContent] = Security.Authenticated.async {
    for (ret <- constantRuleOp.getRules()) yield
      Ok(Json.toJson(ret))
  }

  def upsertConstantRule(): Action[JsValue] = Security.Authenticated.async(BodyParsers.parse.json) {
    implicit request =>
      val ret = request.body.validate[ConstantRule]
      ret.fold(
        error => {
          Logger.error(JsError.toJson(error).toString())
          Future {
            BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error).toString()))
          }
        },
        rule => {
          for (_ <- constantRuleOp.upsert(rule)) yield
            Ok(Json.obj("ok" -> true))
        })
  }

  def deleteConstantRule(monitor: String, monitorType: String): Action[AnyContent] = Security.Authenticated.async {
    val id = ConstantRuleID(monitor, monitorType)
    for (_ <- constantRuleOp.delete(id)) yield
      Ok(Json.obj("ok" -> true))
  }

  def getVariationRules(): Action[AnyContent] = Security.Authenticated.async {
    for (ret <- variationRuleOp.getRules()) yield
      Ok(Json.toJson(ret))
  }

  def upsertVariationRule(): Action[JsValue] = Security.Authenticated.async(BodyParsers.parse.json) {
    implicit request =>
      val ret = request.body.validate[VariationRule]
      ret.fold(
        error => {
          Logger.error(JsError.toJson(error).toString())
          Future {
            BadRequest(Json.obj("ok" -> false, "msg" -> JsError.toJson(error).toString()))
          }
        },
        rule => {
          for (_ <- variationRuleOp.upsert(rule)) yield
            Ok(Json.obj("ok" -> true))
        })
  }

  def deleteVariationRule(monitor: String, monitorType: String): Action[AnyContent] = Security.Authenticated.async {
    val id = VariationRuleID(monitor, monitorType)
    for (_ <- variationRuleOp.delete(id)) yield
      Ok(Json.obj("ok" -> true))
  }

  case class AuditResult(ok:Boolean, count:Int)
  def executeEngineAudit = Security.Authenticated.async(BodyParsers.parse.json) {
    implicit request =>
      import EngineAudit._
      val retParam = request.body.validate[EngineAuditParam]
      retParam.fold(
        error => {
          Logger.error(JsError.toJson(error).toString())
          Future.successful(BadRequest(Json.obj("ok" -> false, "message" -> JsError.toJson(error))))
        },
        param => {
          // ensure calculated type
          monitorTypeDB.calculatedMonitorTypes.foreach(mt=>{
            recordDB.ensureMonitorType(mt._id)
          })

          for(ret<-EngineAudit.audit(recordDB, monitorTypeDB)(param)) yield {
            implicit val write = Json.writes[AuditResult]
            Ok(Json.toJson(AuditResult(true, ret.sum)))
          }
        }
      )
  }

  case class RevertResult(ok:Boolean, count:Int)
  def revertEngineAudit() = Security.Authenticated.async(BodyParsers.parse.json) {
    implicit request =>
      import EngineAudit._
      val retParam = request.body.validate[RevertEngineAuditParam]
      retParam.fold(
        error => {
          Logger.error(JsError.toJson(error).toString())
          Future.successful(BadRequest(Json.obj("ok" -> false, "message" -> JsError.toJson(error))))
        },
        param => {
          for(ret<-EngineAudit.revert(recordDB)(param)) yield {
            implicit val write = Json.writes[RevertResult]
            Ok(Json.toJson(RevertResult(true, ret)))
          }
        }
      )
  }
}
