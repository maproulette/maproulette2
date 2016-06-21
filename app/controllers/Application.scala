package controllers

import javax.inject.Inject

import org.maproulette.Config
import org.maproulette.actions._
import org.maproulette.controllers.ControllerHelper
import org.maproulette.exception.{NotFoundException, StatusMessage, StatusMessages}
import org.maproulette.models.Task
import org.maproulette.models.dal._
import org.maproulette.permissions.Permission
import org.maproulette.session.{SessionManager, User}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.{JsNumber, JsString, Json}
import play.api.mvc._
import play.api.routing._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Promise
import scala.util.{Failure, Success}

class Application @Inject() (val messagesApi: MessagesApi,
                             override val webJarAssets: WebJarAssets,
                             sessionManager:SessionManager,
                             override val dalManager: DALManager,
                             permission: Permission,
                             val config:Config) extends Controller with I18nSupport with ControllerHelper with StatusMessages {

  def clearCaches = Action.async { implicit request =>
    implicit val requireSuperUser = true
    sessionManager.authenticatedRequest { implicit user =>
      dalManager.user.clearCaches
      dalManager.project.clearCaches
      dalManager.challenge.clearCaches
      dalManager.survey.clearCaches
      dalManager.task.clearCaches
      dalManager.tag.clearCaches
      Ok(Json.toJson(StatusMessage("OK", JsString("All caches cleared."))))
    }
  }

  /**
    * The primary entry point to the application
    *
    * @return The index HTML
    */
  def index = Action.async { implicit request =>
    sessionManager.userAwareUIRequest { implicit user =>
      val userOrMocked = User.userOrMocked(user)
      getOkIndex("MapRoulette", userOrMocked, views.html.main(userOrMocked, config.isDebugMode))
    }
  }

  /**
    * Maps a challenge onto the map, this will basically start the challenge with that ID
    *
    * @param parentId The parent chalenge ID
    * @return
    */
  def mapChallenge(parentId:Long) = map(parentId, -1, false)

  def viewChallenge(parentId:Long) = map(parentId, -1, true)

  def mapTask(parentId:Long, taskId:Long) = map(parentId, taskId, false)

  /**
    * Only slightly different to the index page, this one shows the geojson of a specific item on the
    * map, which then can be edited or status set
    *
    * @param parentId The parent of the task (either challenge or survey)
    * @param taskId The task itself
    * @return The html view to show the user
    */
  def map(parentId:Long, taskId:Long, view:Boolean) = Action.async { implicit request =>
    sessionManager.userAwareUIRequest { implicit user =>
      val userOrMocked = User.userOrMocked(user)
      getOkIndex("MapRoulette", userOrMocked, views.html.main(userOrMocked, config.isDebugMode, parentId, taskId, view))
    }
  }

  def adminUIProjectList() = adminUIList(Actions.ITEM_TYPE_PROJECT_NAME, "", None)
  def adminUIChildList(itemType:String, parentId:Long) = adminUIList(itemType, "", Some(parentId))

  /**
    * The generic function used to list elements in the UI
    *
    * @param itemType The type of function you are listing the elements for
    * @param parentId The parent of the objects to list
    * @return The html view to show the user
    */
  protected def adminUIList(itemType:String, parentType:String, parentId:Option[Long]=None) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      // For now we are ignoring the limit and offset properties and letting the UI handle it completely
      val limitIgnore = 10000
      val offsetIgnore = 0
      val view = Actions.getItemType(itemType) match {
        case Some(it) => it match {
          case ProjectType() =>
            val projectCounts = dalManager.project.getChildrenCounts(user, -1)
            views.html.admin.project(user,
              dalManager.project.listManagedProjects(user, limitIgnore, offsetIgnore, false).map(p => {
                val pCounts = projectCounts.getOrElse(p.id, (0, 0))
                (p, pCounts._1, pCounts._2)
              }
            ))
          case ChallengeType() | SurveyType() =>
            dalManager.project.retrieveById(parentId.get) match {
              case Some(p) =>
                permission.hasWriteAccess(ProjectType(), user)(parentId.get)
                val challenges = dalManager.project.listChildren(limitIgnore, offsetIgnore, false)(parentId.get)
                val challengeData = challenges.map(c => {
                  val summary = dalManager.challenge.getSummary(c.id)
                  (c,
                    summary.valuesIterator.sum,
                    summary.filter(_._1 == Task.STATUS_FIXED).values.headOption.getOrElse(0),
                    summary.filter(_._1 == Task.STATUS_FALSE_POSITIVE).values.headOption.getOrElse(0)
                    )
                })
                views.html.admin.challenge(user, parentId.get, p.enabled, challengeData)
              case None => throw new NotFoundException("Parent project for challenge not found")

            }
          case _ => views.html.error.error("Invalid item type requested.")
        }
        case None => views.html.error.error("Invalid item type requested.")
      }
      getOkIndex("MapRoulette Administration", user, view)
    }
  }

  def adminUITaskList(projectId:Long, parentType:String, parentId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      permission.hasWriteAccess(ProjectType(), user)(projectId)
      getOkIndex("MapRoulette Administration", user, views.html.admin.task(user, projectId, parentType, parentId))
    }
  }

  def metrics(survey:Int) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      if (survey == 1) {
        getOkIndex("MapRoulette Metrics", user, views.html.metrics.surveyMetrics(user, config, None, ""))
      } else {
        getOkIndex("MapRoulette Metrics", user, views.html.metrics.challengeMetrics(user, config, None, ""))
      }
    }
  }

  def challengeMetrics(challengeId:Long, projects:String, survey:Int) = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      if (survey == 1) {
        getOkIndex("MapRoulette Metrics", user,
          views.html.metrics.surveyMetrics(user, config, dalManager.survey.retrieveById(challengeId), projects))
      } else {
        getOkIndex("MapRoulette Metrics", user,
          views.html.metrics.challengeMetrics(user, config, dalManager.challenge.retrieveById(challengeId), projects))
      }
    }
  }

  def users(limit:Int, offset:Int, q:String) = Action.async { implicit request =>
    implicit val requireSuperUser = true
    sessionManager.authenticatedUIRequest { implicit user =>
      getOkIndex("MapRoulette Users", user,
        views.html.admin.users.users(user,
          dalManager.user.list(limit, offset, false, q).map(u => {
            val projectList = if (u.isSuperUser) {
              List.empty
            } else {
              dalManager.project.listManagedProjects(u)
            }
            (u, projectList)
          }),
          dalManager.project.listManagedProjects(user)
        )
      )
    }
  }

  def profile = Action.async { implicit request =>
    sessionManager.authenticatedUIRequest { implicit user =>
      getOkIndex("MapRoulette Profile", user, views.html.user.profile(user))
    }
  }

  /**
    * Action to refresh the user's OSM profile, this will reload the index page
    *
    * @return The index html
    */
  def refreshProfile = Action.async { implicit request =>
    sessionManager.authenticatedFutureUIRequest { implicit user =>
      val p = Promise[Result]
      sessionManager.refreshProfile(user.osmProfile.requestToken, user) onComplete {
        case Success(result) => p success Redirect(routes.Application.index())
        case Failure(f) => p failure f
      }
      p.future
    }
  }

  /**
    * Routes to the error page
    *
    * @param error
    * @return
    */
  def error(error:String) = Action.async { implicit request =>
    sessionManager.userAwareUIRequest { implicit user =>
      getOkIndex("MapRoulette Error", User.userOrMocked(user), views.html.error.error(error))
    }
  }

  /**
    * Special API for handling data table API requests for tasks
    *
    * @param parentType Either "Challenge" or "Survey"
    * @param parentId The id of the parent
    * @return
    */
  def taskDataTableList(parentType:String, parentId:Long) = Action.async { implicit request =>
    sessionManager.authenticatedRequest { implicit user =>
      val parentDAL = Actions.getItemType(parentType) match {
        case Some(pt) => pt match {
          case ChallengeType() => Some(dalManager.challenge)
          case SurveyType() => Some(dalManager.survey)
        }
        case None => None
      }
      val postData = request.body.asInstanceOf[AnyContentAsFormUrlEncoded].data
      val draw = postData.get("draw").head.head.toInt
      val start = postData.get("start").head.head.toInt
      val length = postData.get("length").head.head.toInt
      val search = postData.get("search[value]").head.head
      val orderDirection = postData.get("order[0][dir]").head.head.toUpperCase
      val orderColumnID = postData.get("order[0][column]").head.head.toInt
      val orderColumnName = postData.get(s"columns[$orderColumnID][name]").head.head
      val response = parentDAL match {
        case Some(dal) =>
          val tasks = dal.listChildren(length, start, false, search, orderColumnName, orderDirection)(parentId)
          val taskMap = tasks.map(task => Map(
            "id" -> task.id.toString,
            "priority" -> task.priority.toString,
            "name" -> task.name,
            "instruction" -> task.instruction.getOrElse(""),
            "location" -> task.location.toString,
            "status" -> Task.getStatusName(task.status.getOrElse(0)).getOrElse("Unknown"),
            "actions" -> task.id.toString
          ))

          Json.obj(
            "draw" -> JsNumber(draw),
            "recordsTotal" -> JsNumber(dal.getTotalChildren()(parentId)),
            "recordsFiltered" -> JsNumber(dal.getTotalChildren(searchString = search)(parentId)),
            "data" -> Json.toJson(taskMap)
          )
        case None =>
          Json.obj(
            "draw" -> JsNumber(draw),
            "error" -> "Invalid parent type."
          )
      }
      Ok(response)
    }
  }

  /**
    * Maps specific actions to javascripts reverse routes, so that we can call the actions in javascript
    *
    * @return The results of whatever action is called by the javascript
    */
  def javascriptRoutes = Action { implicit request =>
    Ok(
      JavaScriptReverseRouter("jsRoutes")(
        routes.javascript.AuthController.generateAPIKey,
        routes.javascript.AuthController.deleteUser,
        routes.javascript.AuthController.addUserToProject,
        routes.javascript.Application.error,
        routes.javascript.Application.challengeMetrics,
        routes.javascript.FormEditController.rebuildChallenge,
        org.maproulette.controllers.api.routes.javascript.ProjectController.delete,
        org.maproulette.controllers.api.routes.javascript.ChallengeController.delete,
        org.maproulette.controllers.api.routes.javascript.SurveyController.delete,
        org.maproulette.controllers.api.routes.javascript.TaskController.delete,
        org.maproulette.controllers.api.routes.javascript.ProjectController.find,
        org.maproulette.controllers.api.routes.javascript.ChallengeController.find,
        org.maproulette.controllers.api.routes.javascript.SurveyController.find,
        org.maproulette.controllers.api.routes.javascript.ChallengeController.getChallenge,
        org.maproulette.controllers.api.routes.javascript.ProjectController.read,
        org.maproulette.controllers.api.routes.javascript.ChallengeController.read,
        org.maproulette.controllers.api.routes.javascript.SurveyController.read,
        org.maproulette.controllers.api.routes.javascript.TagController.getTags,
        org.maproulette.controllers.api.routes.javascript.TaskController.setTaskStatus,
        org.maproulette.controllers.api.routes.javascript.DataController.getChallengeSummary,
        org.maproulette.controllers.api.routes.javascript.DataController.getChallengeSummaries,
        org.maproulette.controllers.api.routes.javascript.SurveyController.answerSurveyQuestion,
        org.maproulette.controllers.api.routes.javascript.DataController.getChallengeActivity,
        org.maproulette.controllers.api.routes.javascript.DataController.getProjectActivity,
        org.maproulette.controllers.api.routes.javascript.DataController.getProjectSummary,
        org.maproulette.controllers.api.routes.javascript.DataController.getUserSummary,
        org.maproulette.controllers.api.routes.javascript.DataController.getUserChallengeSummary,
        org.maproulette.controllers.api.routes.javascript.ChallengeController.getChallengeGeoJSON,
        routes.javascript.MappingController.getTaskDisplayGeoJSON,
        routes.javascript.MappingController.getSequentialNextTask,
        routes.javascript.MappingController.getSequentialPreviousTask,
        routes.javascript.MappingController.getRandomNextTask,
        routes.javascript.MappingController.getRandomNextTaskWithPriority,
        routes.javascript.Application.mapChallenge
      )
    ).as("text/javascript")
  }
}
