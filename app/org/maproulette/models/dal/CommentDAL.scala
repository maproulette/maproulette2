// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm.SqlParser.{get, long}
import anorm.{RowParser, SQL, ~}
import javax.inject.{Inject, Provider, Singleton}
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.models.utils.{DALHelper, TransactionManager}
import org.maproulette.models.{Comment, Task}
import org.maproulette.permissions.Permission
import org.maproulette.session.User
import play.api.db.Database

/**
  * @author mcuthbert
  */
@Singleton
class CommentDAL @Inject() (
    override val db: Database,
    permission: Permission,
    notificationDAL: Provider[NotificationDAL],
    taskDAL: TaskDAL
) extends DALHelper
    with TransactionManager {

  val commentParser: RowParser[Comment] = {
    get[Long]("task_comments.id") ~
      get[Long]("task_comments.osm_id") ~
      get[String]("users.name") ~
      get[Long]("task_comments.task_id") ~
      get[Long]("task_comments.challenge_id") ~
      get[Long]("task_comments.project_id") ~
      get[DateTime]("task_comments.created") ~
      get[String]("task_comments.comment") ~
      get[Option[Long]]("task_comments.action_id") map {
      case id ~ osm_id ~ osm_name ~ taskId ~ challengeId ~ projectId ~ created ~ comment ~ action_id =>
        Comment(id, osm_id, osm_name, taskId, challengeId, projectId, created, comment, action_id)
    }
  }

  /**
    * Updates a comment that a user previously set
    *
    * @param user           The user updating the comment, it has to be the original user who made the comment
    * @param commentId      The id for the original comment
    * @param updatedComment The new comment
    * @param c              Implicit provided optional connection
    * @return The updated comment
    */
  def update(user: User, commentId: Long, updatedComment: String)(
      implicit c: Option[Connection] = None
  ): Comment = {
    withMRConnection { implicit c =>
      if (StringUtils.isEmpty(updatedComment)) {
        throw new InvalidException("Invalid empty string supplied.")
      }
      // first get the comment
      this.retrieve(commentId) match {
        case Some(original) =>
          if (!user.isSuperUser && original.osm_id != user.osmProfile.id) {
            throw new IllegalAccessException(
              "User updating the comment must be a Super user or the original user who made the comment"
            )
          }
          SQL("UPDATE task_comments SET comment = {comment} WHERE id = {id}")
            .on(Symbol("comment") -> updatedComment, Symbol("id") -> commentId)
            .executeUpdate()
          original.copy(comment = updatedComment)
        case None => throw new NotFoundException("Original comment does not exist")
      }
    }
  }

  /**
    * Retrieves a specific comment
    *
    * @param commentId The id for the comment
    * @param c         Implicit provided optional connection
    * @return An optional comment
    */
  def retrieve(commentId: Long)(implicit c: Option[Connection] = None): Option[Comment] = {
    withMRConnection { implicit c =>
      SQL(
        """SELECT * FROM task_comments tc
              INNER JOIN users u ON u.osm_id = tc.osm_id
              WHERE tc.id = {commentId}"""
      ).on(Symbol("commentId") -> commentId).as(this.commentParser.*).headOption
    }
  }

  /**
    * Deletes a comment from a task
    *
    * @param user      The user deleting the comment, only super user or challenge admin can delete
    * @param taskId    The task that the comment is associated with
    * @param commentId The id for the comment being deleted
    * @param c         Implicit provided optional connection
    */
  def delete(user: User, taskId: Long, commentId: Long)(
      implicit c: Option[Connection] = None
  ): Unit = {
    withMRConnection { implicit c =>
      this.taskDAL.retrieveById(taskId) match {
        case Some(task) =>
          this.permission.hasObjectAdminAccess(task, user)
          SQL("DELETE FROM task_comments WHERE id = {id}").on(Symbol("id") -> commentId)
        case None =>
          throw new NotFoundException("Task was not found.")
      }
    }
  }

  /**
    * Retrieves all the comments for a task, challenge or project
    *
    * @param projectIdList   A list of all project ids to match on
    * @param challengeIdList A list of all challenge ids to match on
    * @param taskIdList      A list of all task ids to match on
    * @param limit           limit the number of tasks in the response
    * @param offset          for paging
    * @param c               Implicit provided optional connection
    * @return The list of comments for the task
    */
  def retrieveComments(
      projectIdList: List[Long],
      challengeIdList: List[Long],
      taskIdList: List[Long],
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0
  )(implicit c: Option[Connection] = None): List[Comment] = {
    withMRConnection { implicit c =>
      val whereClause = new StringBuilder("")
      if (projectIdList.nonEmpty) {
        var vpSearch =
          s"""OR 1 IN (SELECT 1 FROM unnest(ARRAY[${projectIdList.mkString(",")}]) AS pIds
                         WHERE pIds IN (SELECT vp.project_id FROM virtual_project_challenges vp
                                        WHERE vp.challenge_id = tc.challenge_id))"""

        this.appendInWhereClause(
          whereClause,
          s"project_id IN (${projectIdList.mkString(",")}) $vpSearch"
        )
      }
      if (challengeIdList.nonEmpty) {
        this.appendInWhereClause(whereClause, s"challenge_id IN (${challengeIdList.mkString(",")})")
      }
      if (taskIdList.nonEmpty) {
        this.appendInWhereClause(whereClause, s"task_id IN (${taskIdList.mkString(",")})")
      }

      SQL(
        s"""
              SELECT * FROM task_comments tc
              INNER JOIN users u ON u.osm_id = tc.osm_id
              WHERE $whereClause
              ORDER BY tc.project_id, tc.challenge_id, tc.created DESC
              LIMIT ${this.sqlLimit(limit)} OFFSET $offset
          """
      ).as(this.commentParser.*)
    }
  }

  /**
    * Add comment to a task
    *
    * @param user     The user adding the comment
    * @param task     The task that you are adding the comment too
    * @param comment  The actual comment
    * @param actionId the id for the action if any action associated
    * @param c        Implicit provided optional connection
    */
  def add(user: User, task: Task, comment: String, actionId: Option[Long])(
      implicit c: Option[Connection] = None
  ): Comment = {
    withMRConnection { implicit c =>
      if (StringUtils.isEmpty(comment)) {
        throw new InvalidException("Invalid empty string supplied.")
      }
      val query =
        s"""
           |INSERT INTO task_comments (osm_id, task_id, comment, action_id)
           |VALUES ({osm_id}, {task_id}, {comment}, {action_id}) RETURNING id, project_id, challenge_id
         """.stripMargin
      SQL(query)
        .on(
          Symbol("osm_id")    -> user.osmProfile.id,
          Symbol("task_id")   -> task.id,
          Symbol("comment")   -> comment,
          Symbol("action_id") -> actionId
        )
        .as((long("id") ~ long("project_id") ~ long("challenge_id")).*)
        .headOption match {
        case Some(ids) =>
          val newComment =
            Comment(
              ids._1._1,
              user.osmProfile.id,
              user.name,
              task.id,
              ids._1._2,
              ids._2,
              DateTime.now(),
              comment,
              actionId
            )
          this.notificationDAL.get().createMentionNotifications(user, newComment, task)
          newComment
        case None => throw new Exception("Failed to add comment")
      }
    }
  }
}
