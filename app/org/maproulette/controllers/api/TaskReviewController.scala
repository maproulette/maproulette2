// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject
import org.maproulette.Config
import org.maproulette.data.ActionManager
import org.maproulette.models.dal._
import org.maproulette.models.{Challenge, Task}
import org.maproulette.permissions.Permission
import org.maproulette.session.{SessionManager, SearchParameters, User}
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._

/**
  * TaskReviewController is responsible for handling functionality related to
  * task reviews.
  *
  * @author krotstan
  */
class TaskReviewController @Inject()(override val sessionManager: SessionManager,
                               override val actionManager: ActionManager,
                               override val dal: TaskDAL,
                               override val tagDAL: TagDAL,
                               taskReviewDAL: TaskReviewDAL,
                               dalManager: DALManager,
                               wsClient: WSClient,
                               config: Config,
                               components: ControllerComponents,
                               override val bodyParsers: PlayBodyParsers)
  extends TaskController(sessionManager, actionManager, dal, tagDAL, dalManager,
                         wsClient, config, components, bodyParsers) {


  /**
    * Gets and claims a task that needs to be reviewed.
    *
    * @param id Task id to work on
    * @return
    */
  def startTaskReview(id:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val task = this.dal.retrieveById(id) match {
        case Some(t) => t
        case None => throw new NotFoundException(s"Task with $id not found, cannot start review.")
      }

      val result = this.taskReviewDAL.startTaskReview(user, task)
      Ok(Json.toJson(result))
    }
  }

  /**
    * Releases a claim on a task that needs to be reviewed.
    *
    * @param id Task id to work on
    * @return
    */
  def cancelTaskReview(id:Long) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val task = this.dal.retrieveById(id) match {
        case Some(t) => t
        case None => throw new NotFoundException(s"Task with $id not found, cannot cancel review.")
      }

      val result = this.taskReviewDAL.cancelTaskReview(user, task)
      Ok(Json.toJson(result))
    }
  }

  /**
    * Gets and claims the next task that needs to be reviewed.
    *
    * Valid search parameters include:
    * cs => "my challenge name"
    * o => "mapper's name"
    * r => "reviewer's name"
    *
    * @return Task
    */
  def nextTaskReview(onlySaved: Boolean=false, sort:String, order:String) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        val result = this.taskReviewDAL.nextTaskReview(user, params, onlySaved, sort, order)
        val nextTask = result match {
          case Some(task) =>
            Ok(Json.toJson(this.taskReviewDAL.startTaskReview(user, task)))
          case None =>
            throw new NotFoundException("No tasks found to review.")
        }

        nextTask
      }
    }
  }

  /**
    * Gets tasks where a review is requested
    *
    * @param startDate Optional start date to filter by reviewedAt date
    * @param endDate Optional end date to filter by reviewedAt date
    * @param limit The number of tasks to return
    * @param page The page number for the results
    * @param sort The column to sort
    * @param order The order direction to sort
    * @return
    */
  def getReviewRequestedTasks(startDate: String=null, endDate: String=null, onlySaved: Boolean=false,
                              limit:Int, page:Int, sort:String, order:String) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        //cs => "my challenge name"
        //o => "mapper's name"
        //r => "reviewer's name"
        val (count, result) = this.taskReviewDAL.getReviewRequestedTasks(User.userOrMocked(user), params,
           startDate, endDate, onlySaved, limit, page, sort, order)
        Ok(Json.obj("total" -> count, "tasks" -> _insertExtraJSON(result)))
      }
    }
  }

  /**
    * Gets reviewed tasks where the user has reviewed or requested review
    *
    * @param startDate Optional start date to filter by reviewedAt date
    * @param endDate Optional end date to filter by reviewedAt date
    * @param asReviewer Whether we should return tasks reviewed by this user or reqested by this user
    * @param allowReviewNeeded Whether we should return tasks where status is review requested also
    * @param limit The number of tasks to return
    * @param page The page number for the results
    * @param sort The column to sort
    * @param order The order direction to sort
    * @return
    */
  def getReviewedTasks(startDate: String=null, endDate: String=null,
                       asReviewer: Boolean=false, allowReviewNeeded: Boolean=false,
                       limit:Int, page:Int,
                       sort:String, order:String) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        val (count, result) = this.taskReviewDAL.getReviewedTasks(User.userOrMocked(user), params,
             startDate, endDate, asReviewer, allowReviewNeeded, limit, page, sort, order)
        Ok(Json.obj("total" -> count, "tasks" -> _insertExtraJSON(result)))
      }
    }
  }

  /**
   * Fetches the matching parent object and inserts it (id, name, status)
   * into the JSON data returned. Also fetches and inserts usernames for
   * 'reviewRequestedBy' and 'reviewBy'
   */
  private def _insertExtraJSON(tasks: List[Task]): JsValue = {
    if (tasks.isEmpty) {
      Json.toJson(List[JsValue]())
    } else {
      val fetchedChallenges = this.dalManager.challenge.retrieveListById(-1, 0)(tasks.map(t =>t.parent))

      val projects = Some(this.dalManager.project.retrieveListById(-1, 0)(fetchedChallenges.map(c => c.general.parent)).map(p =>
                                 p.id -> Json.obj("id" -> p.id, "name" -> p.name, "displayName" -> p.displayName)).toMap)

      val challenges = Some(fetchedChallenges.map(c => c.id ->
                          Json.obj("id" -> c.id, "name" -> c.name, "status" -> c.status,
                                   "parent" -> Json.toJson(projects.get(c.general.parent)).as[JsObject])).toMap)

      val mappers = Some(this.dalManager.user.retrieveListById(-1, 0)(tasks.map(
        t => t.reviewRequestedBy.getOrElse(0L))).map(u =>
          u.id -> Json.obj("username" -> u.name, "id" -> u.id)).toMap)

      val reviewers = Some(this.dalManager.user.retrieveListById(-1, 0)(tasks.map(
        t => t.reviewedBy.getOrElse(0L))).map(u =>
          u.id -> Json.obj("username" -> u.name, "id" -> u.id)).toMap)

      val jsonList = tasks.map { task =>
        val challengeJson = Json.toJson(challenges.get(task.parent)).as[JsObject]
        var updated = Utils.insertIntoJson(Json.toJson(task), Challenge.KEY_PARENT, challengeJson, true)
        if (task.reviewRequestedBy.getOrElse(0) != 0) {
          val mapperJson = Json.toJson(mappers.get(task.reviewRequestedBy.get)).as[JsObject]
          updated = Utils.insertIntoJson(updated, "reviewRequestedBy", mapperJson, true)
        }
        if (task.reviewedBy.getOrElse(0) != 0) {
          val reviewerJson = Json.toJson(reviewers.get(task.reviewedBy.get)).as[JsObject]
          updated = Utils.insertIntoJson(updated, "reviewedBy", reviewerJson, true)
        }

        updated
      }
      Json.toJson(jsonList)
    }
  }

  /**
    * Gets reviewed tasks where the user has reviewed or requested review
    *
    * @param reviewTasksType - 1: To Be Reviewed 2: User's reviewed Tasks 3: All reviewed by users
    * @param startDate Optional start date to filter by reviewedAt date
    * @param endDate Optional end date to filter by reviewedAt date
    * @return
    */
  def getReviewMetrics(reviewTasksType: Int, startDate: String=null, endDate: String=null,
                       onlySaved: Boolean=false) : Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        val result = this.taskReviewDAL.getReviewMetrics(User.userOrMocked(user), reviewTasksType, params, startDate, endDate, onlySaved)
        Ok(Json.toJson(result))
      }
    }
  }

  /**
    * Gets clusters of review tasks. Uses kmeans method in postgis.
    *
    * @param reviewTasksType Type of review tasks (1: To Be Reviewed 2: User's reviewed Tasks 3: All reviewed by users)
    * @param numberOfPoints Number of clustered points you wish to have returned
    * @param startDate Optional start date to filter by reviewedAt date
    * @param endDate Optional end date to filter by reviewedAt date
    * @return A list of ClusteredPoint's that represent clusters of tasks
    */
  def getReviewTaskClusters(reviewTasksType: Int, numberOfPoints: Int, startDate: String=null, endDate: String=null,
                            onlySaved: Boolean=false): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      SearchParameters.withSearch { implicit params =>
        Ok(Json.toJson(this.taskReviewDAL.getReviewTaskClusters(User.userOrMocked(user), reviewTasksType, params, numberOfPoints, startDate, endDate, onlySaved)))
      }
    }
  }

}
