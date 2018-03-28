package org.maproulette.controllers.api

import javax.inject.Inject

import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.actions.{ActionManager, TaskViewed, VirtualChallengeType}
import org.maproulette.controllers.CRUDController
import org.maproulette.exception.NotFoundException
import org.maproulette.models.{ClusteredPoint, Task, VirtualChallenge}
import org.maproulette.models.dal.{TaskDAL, VirtualChallengeDAL}
import org.maproulette.session.{SearchLocation, SearchParameters, SessionManager, User}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.duration.Duration

/**
  * @author mcuthbert
  */
class VirtualChallengeController @Inject() (override val sessionManager: SessionManager,
                                            override val actionManager: ActionManager,
                                            override val dal:VirtualChallengeDAL,
                                            taskDAL: TaskDAL,
                                            config:Config) extends CRUDController[VirtualChallenge] {
  override implicit val tReads: Reads[VirtualChallenge] = VirtualChallenge.virtualChallengeReads
  override implicit val tWrites: Writes[VirtualChallenge] = VirtualChallenge.virtualChallengeWrites
  override implicit val itemType = VirtualChallengeType()

  //writes for tasks
  implicit val taskWrites: Writes[Task] = Task.TaskFormat
  implicit val taskReads: Reads[Task] = Task.TaskFormat
  //reads and writes for Search Parameters
  implicit val locationWrites = Json.writes[SearchLocation]
  implicit val locationReads = Json.reads[SearchLocation]
  implicit val paramsWrites = Json.writes[SearchParameters]
  implicit val paramsReads = Json.reads[SearchParameters]


  /**
    * This function allows sub classes to modify the body, primarily this would be used for inserting
    * default elements into the body that shouldn't have to be required to create an object.
    *
    * @param body The incoming body from the request
    * @param user The user executing the request
    * @return
    */
  override def updateCreateBody(body: JsValue, user: User): JsValue = {
    val jsonBody = super.updateCreateBody(body, user)
    // if expiry is set as a hours from now, pull it out and convert to timestamp.
    val expiryValue = (jsonBody \ "expiry").asOpt[String] match {
      case Some(ex) => Duration(ex).toMillis.toInt
      case None => config.virtualChallengeExpiry.toMillis.toInt
    }
    val expiryUpdate = Utils.insertIntoJson(jsonBody, "expiry", DateTime.now().plusMillis(expiryValue), true)(DefaultJodaDateWrites)
    val searchUpdate = Utils.insertIntoJson(expiryUpdate, "searchParameters", SearchParameters())
    Utils.insertIntoJson(searchUpdate, "ownerId", user.osmProfile.id)
  }

  /**
    * Lists all the tasks for the virtual challenge
    *
    * @param id The id of the virtual challenge that you are listing the tasks for
    * @param limit Limit the number of tasks returned
    * @param offset paging offset
    * @return
    */
  def listTasks(id:Long, limit:Int, offset:Int) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.dal.listTasks(id, User.userOrMocked(user), limit, offset)))
    }
  }

  /**
    * Rebuilds the challenge to take into account any new tasks that can be found in from the original
    * search parameters
    *
    * @param id The id of the virtual challenge
    * @return
    */
  def rebuildVirtualChallenge(id:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { p =>
        this.dal.rebuildVirtualChallenge(id, p, User.userOrMocked(user))
        Ok
      }
    }
  }

  /**
    * Gets a random task from the list of tasks within the virtual challenge
    *
    * @param id
    * @return
    */
  def getRandomTask(id:Long, proximity:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { p =>
        val proximityOption = if (proximity < 0) {
          None
        } else {
          Some(proximity)
        }
        Ok(Json.toJson(this._getRandomTask(id, p, user, proximityOption)))
      }
    }
  }

  /**
    * Gets a random task from the list of tasks within the virtual challenge
    *
    * @param name
    * @return
    */
  def getRandomTask(name:String, proximity:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { p =>
        this.dal.retrieveByName(name) match {
          case Some(vc) =>
            val proximityOption = if (proximity < 0) {
              None
            } else {
              Some(proximity)
            }
            Ok(Json.toJson(this._getRandomTask(vc.id, p, user, proximityOption)))
          case None => throw new NotFoundException("No Virtual Challenge found with that challenge name.")
        }
      }
    }
  }

  /**
    * Gets the next task in sequential order for the specified virtual challenge
    *
    * @param challengeId The current virtual challenge id
    * @param currentTaskId The current task id that is being viewed
    * @return The next task in the list
    */
  def getSequentialNextTask(challengeId:Long, currentTaskId:Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Utils.getResponseJSON(this.dal.getSequentialNextTask(challengeId, currentTaskId), this.taskDAL.getLastModifiedUser));
    }
  }

  /**
    * Gets the previous task in sequential order for the specified challenge
    *
    * @param challengeId The current virtual challenge id
    * @param currentTaskId The current task id that is being viewed
    * @return The previous task in the list
    */
  def getSequentialPreviousTask(challengeId:Long, currentTaskId:Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Utils.getResponseJSON(this.dal.getSequentialPreviousTask(challengeId, currentTaskId), this.taskDAL.getLastModifiedUser));
    }
  }

  /**
    * Gets the geo json for all the tasks associated with the challenge
    *
    * @param challengeId  The challenge with the geojson
    * @param statusFilter Filtering by status of the tasks
    * @return
    */
  def getVirtualChallengeGeoJSON(challengeId: Long, statusFilter: String): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      this.dal.retrieveById(challengeId) match {
        case Some(c) =>
          val filter = if (StringUtils.isEmpty(statusFilter)) {
            None
          } else {
            Some(Utils.split(statusFilter).map(_.toInt))
          }
          Ok(Json.parse(this.dal.getChallengeGeometry(challengeId, filter)))
        case None => throw new NotFoundException(s"No virtual challenge with id $challengeId found.")
      }
    }
  }

  def getClusteredPoints(challengeId: Long, statusFilter: String): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      implicit val writes = ClusteredPoint.clusteredPointWrites
      val filter = if (StringUtils.isEmpty(statusFilter)) {
        None
      } else {
        Some(Utils.split(statusFilter).map(_.toInt))
      }
      Ok(Json.toJson(this.dal.getClusteredPoints(challengeId, filter)))
    }
  }

  private def _getRandomTask(id:Long, params:SearchParameters, user:Option[User], proximity:Option[Long]=None) : Option[Task] = {
    val results = this.dal.getRandomTask(id, params, User.userOrMocked(user), proximity)
    results.foreach(task => this.actionManager.setAction(user, this.itemType.convertToItem(task.id), TaskViewed(), ""))
    results
  }
}
