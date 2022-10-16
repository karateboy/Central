package controllers

import models.{Ability, GroupDB, User, UserDB}
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
case class Credential(user: String, password: String)
import javax.inject._
import models.Group
case class UserData(user:User, group:Group)
/**
 * @author user
 */
class Login @Inject()(userOp: UserDB, groupOp:GroupDB, configuration: Configuration)
  extends Controller {
  implicit val credentialReads = Json.reads[Credential]
  val bypassLogin: Boolean = configuration.getBoolean("logger.bypassLogin").getOrElse(false)

  def authenticate = Action.async(BodyParsers.parse.json) {
    implicit request =>
      implicit val writes = Json.writes[User]
      implicit val w3 = Json.writes[Ability]
      implicit val w1 = Json.writes[Group]
      implicit val w2 = Json.writes[UserData]

      if(bypassLogin){
        val user = userOp.defaultUser
        val userGroup = {
          user.group.getOrElse({
            if(user.isAdmin)
              Group.PLATFORM_ADMIN
            else
              Group.PLATFORM_USER
          })
        }
        val userInfo = UserInfo(user._id, user.name, userGroup, user.isAdmin)
        for(groupOpt <- groupOp.getGroupByIdAsync(userGroup)) yield {
          val group = groupOpt.get
          Ok(Json.obj("ok"->true, "userData"->UserData(user, group))).withSession(Security.setUserinfo(request, userInfo))
        }
      }else{
        val credentail = request.body.validate[Credential]
        credentail.fold(
          error=> Future.successful(BadRequest(Json.obj("ok"->false, "msg"->JsError.toJson(error))))
          ,
          crd=>{
            val userOpt = userOp.getUserByEmail(crd.user)
            if(userOpt.isEmpty || userOpt.get.password != crd.password) {
              Future.successful(Results.Unauthorized(Json.obj("ok"->false, "msg"->"密碼或帳戶錯誤")))
            } else {
              val user = userOpt.get
              val userGroup = {
                user.group.getOrElse({
                  if(user.isAdmin)
                    Group.PLATFORM_ADMIN
                  else
                    Group.PLATFORM_USER
                })
              }
              val userInfo = UserInfo(user._id, user.name, userGroup, user.isAdmin)
              for(groupOp <- groupOp.getGroupByIdAsync(userGroup)) yield {
                val group = groupOp.get
                Ok(Json.obj("ok"->true, "userData"->UserData(user, group))).withSession(Security.setUserinfo(request, userInfo))
              }
            }
          })
      }
  }

  def isLogin = Security.Authenticated {
    Ok(Json.obj("ok"->true))
  }

  def logout = Action{
    Ok("").withNewSession
  }
}