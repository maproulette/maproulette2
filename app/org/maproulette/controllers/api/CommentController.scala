package org.maproulette.controllers.api

import java.net.URLDecoder

import javax.inject.Inject
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.models.dal.DALManager
import org.maproulette.session.SessionManager
import play.api.libs.json.Json
import play.api.mvc._

/**
  * @author mcuthbert
  */
class CommentController @Inject() (
    sessionManager: SessionManager,
    dalManager: DALManager,
    components: ControllerComponents,
    bodyParsers: PlayBodyParsers
) extends AbstractController(components) {

  /**
    * Retrieves a specific comment for the user
    *
    * @param commentId The id of the comment to retrieve
    * @return The comment
    */
  def retrieveComment(commentId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      this.dalManager.comment.retrieve(commentId) match {
        case Some(comment) => Ok(Json.toJson(comment))
        case None          => NotFound
      }
    }
  }

  /**
    * Retrieves all the comments for a Task
    *
    * @param taskId The task to retrieve the comments for
    * @return A list of comments
    */
  def retrieveComments(taskId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(
        Json.toJson(this.dalManager.comment.retrieveComments(List.empty, List.empty, List(taskId)))
      )
    }
  }

  /**
    * Adds a comment for a specific task
    *
    * @param taskId   The id for a task
    * @param comment  The comment the user is leaving
    * @param actionId The action if any associated with the comment
    * @return Ok if successful.
    */
  def addComment(taskId: Long, comment: String, actionId: Option[Long]): Action[AnyContent] =
    Action.async { implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        val task = this.dalManager.task.retrieveById(taskId) match {
          case Some(t) => t
          case None =>
            throw new NotFoundException(s"Task with $taskId not found, can not add comment.")
        }
        Created(
          Json.toJson(
            this.dalManager.comment.add(user, task, URLDecoder.decode(comment, "UTF-8"), actionId)
          )
        )
      }
    }

  /**
    * Adds a comment for tasks in a bundle
    *
    * @param bundleId   The id for the bundle
    * @param comment  The comment the user is leaving
    * @param actionId The action if any associated with the comment
    * @return Ok if successful.
    */
  def addCommentToBundleTasks(
      bundleId: Long,
      comment: String,
      actionId: Option[Long]
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val tasks = this.dalManager.taskBundle.getTaskBundle(user, bundleId).tasks match {
        case Some(t) => t
        case None    => throw new InvalidException("No tasks found in this bundle.")
      }

      for (task <- tasks) {
        this.dalManager.comment.add(user, task, URLDecoder.decode(comment, "UTF-8"), actionId)
      }

      Ok(Json.toJson(this.dalManager.taskBundle.getTaskBundle(user, bundleId)))
    }
  }

  /**
    * Updates the original comment
    *
    * @param commentId The ID of the comment to update
    * @param comment   The comment to update
    * @return
    */
  def updateComment(commentId: Long, comment: String): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        Ok(
          Json.toJson(
            this.dalManager.comment.update(user, commentId, URLDecoder.decode(comment, "UTF-8"))
          )
        )
      }
  }

  /**
    * Deletes a comment from a task
    *
    * @param taskId    The id of the task that the comment is associated with
    * @param commentId The id of the comment that is being deleted
    * @return Ok if successful,
    */
  def deleteComment(taskId: Long, commentId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        this.dalManager.comment.delete(user, taskId, commentId)
        Ok
      }
  }

}
