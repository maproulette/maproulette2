/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.models.dal

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Provider, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.data.Actions
import org.maproulette.framework.service.ServiceManager
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.provider.websockets.WebSocketProvider
import play.api.db.Database
import play.api.libs.ws.WSClient

/**
  * @author krotstan
  */
@Singleton
class TaskHistoryDAL @Inject() (
    override val db: Database,
    override val tagDAL: TagDAL,
    serviceManager: ServiceManager,
    config: Config,
    override val permission: Permission,
    dalManager: Provider[DALManager],
    webSocketProvider: WebSocketProvider,
    ws: WSClient
) extends TaskDAL(
      db,
      tagDAL,
      permission,
      serviceManager,
      config,
      dalManager,
      webSocketProvider,
      ws
    ) {

  private val commentEntryParser: RowParser[TaskLogEntry] = {
    get[Long]("task_comments.task_id") ~
      get[DateTime]("task_comments.created") ~
      get[Int]("users.id") ~
      get[String]("task_comments.comment") map {
      case taskId ~ created ~ userId ~ comment =>
        new TaskLogEntry(
          taskId,
          created,
          TaskLogEntry.ACTION_COMMENT,
          Some(userId),
          None,
          None,
          None,
          None,
          None,
          None,
          Some(comment)
        )
    }
  }

  private val reviewEntryParser: RowParser[TaskLogEntry] = {
    get[Long]("task_id") ~
      get[DateTime]("reviewed_at") ~
      get[Option[DateTime]]("review_started_at") ~
      get[Int]("review_status") ~
      get[Int]("requested_by") ~
      get[Option[Int]]("reviewed_by") map {
      case taskId ~ reviewedAt ~ reviewStartedAt ~ reviewStatus ~ requestedBy ~
            reviewedBy =>
        new TaskLogEntry(
          taskId,
          reviewedAt,
          TaskLogEntry.ACTION_REVIEW,
          None,
          None,
          None,
          reviewStartedAt,
          Some(reviewStatus),
          Some(requestedBy),
          reviewedBy,
          None
        )
    }
  }

  private val statusActionEntryParser: RowParser[TaskLogEntry] = {
    get[Long]("status_actions.task_id") ~
      get[DateTime]("status_actions.created") ~
      get[Option[Int]]("users.id") ~
      get[Int]("status_actions.old_status") ~
      get[Int]("status_actions.status") ~
      get[Option[DateTime]]("status_actions.started_at") map {
      case taskId ~ created ~ userId ~ oldStatus ~ status ~
            startedAt =>
        new TaskLogEntry(
          taskId,
          created,
          TaskLogEntry.ACTION_STATUS_CHANGE,
          userId,
          Some(oldStatus),
          Some(status),
          startedAt,
          None,
          None,
          None,
          None
        )
    }
  }

  private val actionEntryParser: RowParser[TaskLogEntry] = {
    get[Long]("actions.item_id") ~
      get[DateTime]("actions.created") ~
      get[Option[Int]]("users.id") map {
      case taskId ~ created ~ userId =>
        new TaskLogEntry(
          taskId,
          created,
          TaskLogEntry.ACTION_UPDATE,
          userId,
          None,
          None,
          None,
          None,
          None,
          None,
          None
        )
    }
  }

  /**
    * Returns a history log for the task -- includes comments, status actions,
    * review actions
    * @param taskId
    * @return List of TaskLogEntry
    */
  def getTaskHistoryLog(taskId: Long)(implicit c: Option[Connection] = None): List[TaskLogEntry] = {
    this.withMRConnection { implicit c =>
      val comments =
        SQL"""SELECT tc.task_id, tc.created, users.id, tc.comment FROM task_comments tc
              INNER JOIN users on users.osm_id=tc.osm_id
              WHERE task_id = $taskId""".as(this.commentEntryParser.*)

      val reviews =
        SQL"""SELECT * FROM task_review_history WHERE task_id = $taskId""".as(
          this.reviewEntryParser.*
        )

      val statusActions =
        SQL"""SELECT sa.task_id, sa.created, users.id, sa.old_status, sa.status, sa.started_at
              FROM status_actions sa
              LEFT OUTER JOIN users on users.osm_id=sa.osm_user_id
              WHERE task_id = $taskId""".as(this.statusActionEntryParser.*)

      val actions =
        SQL"""SELECT actions.item_id, actions.created, users.id
              FROM actions
              LEFT OUTER JOIN users on users.osm_id=actions.osm_user_id
              WHERE actions.item_id = $taskId AND actions.action = ${Actions.ACTION_TYPE_UPDATED}
           """.as(this.actionEntryParser.*)

      ((comments ++ reviews ++ statusActions ++ actions).sortWith(sortByDate)).reverse
    }
  }

  private def sortByDate(entry1: TaskLogEntry, entry2: TaskLogEntry) = {
    entry1.timestamp.getMillis() < entry2.timestamp.getMillis()
  }
}
