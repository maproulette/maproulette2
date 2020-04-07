/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.data

import anorm.SqlParser.get
import anorm._
import java.sql.Timestamp

import javax.inject.Inject
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.framework.model.User
import org.maproulette.models.Task
import org.maproulette.models.utils.{AND, DALHelper}
import play.api.Application
import play.api.db.Database
import play.api.libs.json.{Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * @author cuthbertm
  */
case class StatusActionItem(
    id: Long,
    created: DateTime,
    osmUserId: Long,
    osmUserName: String,
    projectId: Long,
    projectName: String,
    challengeId: Long,
    challengeName: String,
    taskId: Long,
    oldStatus: Int,
    newStatus: Int
)

case class DailyStatusActionSummary(
    date: DateTime,
    osmUserId: Long,
    osmUserName: String,
    fixedUpdates: Int,
    lastFixedUpdated: Option[DateTime],
    falsePositiveUpdates: Int,
    lastFalsePositiveUpdated: Option[DateTime],
    skippedUpdates: Int,
    lastSkippedUpdated: Option[DateTime],
    alreadyFixedUpdates: Int,
    lastAlreadyFixedUpdated: Option[DateTime],
    tooHardUpdates: Int,
    lastTooHardUpdated: Option[DateTime],
    answeredUpdates: Int,
    lastAnsweredUpdated: Option[DateTime]
)

case class StatusActionLimits(
    startDate: Option[DateTime] = None,
    endDate: Option[DateTime] = None,
    osmUserIds: List[Long] = List.empty,
    projectIds: List[Long] = List.empty,
    challengeIds: List[Long] = List.empty,
    taskIds: List[Long] = List.empty,
    newStatuses: List[Int] = List.empty,
    oldStatuses: List[Int] = List.empty
)

class StatusActionManager @Inject() (config: Config, db: Database)(
    implicit application: Application
) extends DALHelper {

  implicit val actionItemWrites: Writes[StatusActionItem] = Json.writes[StatusActionItem]
  implicit val actionItemReads: Reads[StatusActionItem]   = Json.reads[StatusActionItem]

  /**
    * A anorm row parser for the actions table
    */
  implicit val parser: RowParser[StatusActionItem] = {
    get[Long]("status_actions.id") ~
      get[DateTime]("status_actions.created") ~
      get[Long]("status_actions.osm_user_id") ~
      get[String]("users.name") ~
      get[Long]("status_actions.project_id") ~
      get[String]("projects.name") ~
      get[Long]("status_actions.challenge_id") ~
      get[String]("challenges.name") ~
      get[Long]("status_actions.task_id") ~
      get[Int]("status_actions.old_status") ~
      get[Int]("status_actions.status") map {
      case id ~ created ~ osmUserId ~ osmUserName ~ projectId ~ projectName ~ challengeId ~
            challengeName ~ taskId ~ old_status ~ status => {
        new StatusActionItem(
          id,
          created,
          osmUserId,
          osmUserName,
          projectId,
          projectName,
          challengeId,
          challengeName,
          taskId,
          old_status,
          status
        )
      }
    }
  }

  implicit val dailyParser: RowParser[DailyStatusActionSummary] = {
    get[DateTime]("daily") ~
      get[Long]("status_actions.osm_user_id") ~
      get[String]("users.name") ~
      get[Int]("fixed") ~
      get[Option[DateTime]]("lastFixed") ~
      get[Int]("falsePositive") ~
      get[Option[DateTime]]("lastFalsePositive") ~
      get[Int]("skipped") ~
      get[Option[DateTime]]("lastSkipped") ~
      get[Int]("alreadyFixed") ~
      get[Option[DateTime]]("lastAlreadyFixed") ~
      get[Int]("tooHard") ~
      get[Option[DateTime]]("lastTooHard") ~
      get[Int]("answered") ~
      get[Option[DateTime]]("lastAnswered") map {
      case daily ~ osmUserId ~ osmUsername ~ fixed ~ lastFixed ~ falsePositive ~ lastFalsePositive ~ skipped ~ lastSkipped ~
            alreadyFixed ~ lastAlreadyFixed ~ tooHard ~ lastTooHard ~ answered ~ lastAnswered => {
        new DailyStatusActionSummary(
          daily,
          osmUserId,
          osmUsername,
          fixed,
          lastFixed,
          falsePositive,
          lastFalsePositive,
          skipped,
          lastSkipped,
          alreadyFixed,
          lastAlreadyFixed,
          tooHard,
          lastTooHard,
          answered,
          lastAnswered
        )
      }
    }
  }

  /**
    * The status action is set in a different table for performance and efficiency reasons
    *
    * @param user   The user set the task status
    * @param task   The task that is having it's status set
    * @param status The new updated status that was replaced
    * @return
    */
  def setStatusAction(
      user: User,
      task: Task,
      status: Int,
      startedAt: Option[DateTime] = None
  ): Future[Boolean] = {
    Future {
      val startDate = startedAt match {
        case Some(d) => new Timestamp(d.getMillis())
        case None    => null
      }

      db.withTransaction { implicit c =>
        SQL"""INSERT INTO status_actions (osm_user_id, project_id, challenge_id,
                                          task_id, old_status, status, started_at)
                SELECT ${user.osmProfile.id}, parent_id, ${task.parent},
                       ${task.id}, ${task.status}, $status, ${startDate}
                FROM challenges WHERE id = ${task.parent}
          """.execute()
      }
    }
  }

  /**
    * Retrieves all the status actions executed against a specific task
    *
    * @param task         The task to get all the status actions for
    * @param user         The user executing the task
    * @param statusLimits Set a limit of the type of status sets you want returned
    * @param limit        limit the number of tasks coming back
    * @return a list of StatusActionItems
    */
  def getStatusActions(
      task: Task,
      user: User,
      statusLimits: Option[List[Int]] = None,
      limit: Int = 1
  ): List[StatusActionItem] = {
    db.withConnection { implicit c =>
      val sLimits = statusLimits match {
        case Some(l) => s"""AND sa.status IN (${l.mkString(",")})"""
        case None    => ""
      }

      SQL"""SELECT * FROM status_actions sa
            INNER JOIN users u ON u.osm_id = sa.osm_user_id
            INNER JOIN projects p ON p.id = sa.project_id
            INNER JOIN challenges c ON c.id = sa.challenge_id
            WHERE sa.task_id = ${task.id}
            #${sLimits}
            ORDER BY sa.created DESC
            LIMIT #${sqlLimit(limit)}""".as(this.parser.*)
    }
  }

  def getStatusUpdates(
      user: User,
      statusActionLimits: StatusActionLimits,
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0
  ): List[StatusActionItem] = {
    val parameters  = new ListBuffer[NamedParameter]()
    val whereClause = new StringBuilder()
    whereClause ++= this.getLongListFilter(Some(statusActionLimits.osmUserIds), "sa.osm_user_id")(
      getCurrentConjunction(whereClause)
    )
    whereClause ++= this.getLongListFilter(Some(statusActionLimits.projectIds), "sa.project_id")(
      getCurrentConjunction(whereClause)
    )
    whereClause ++= this.getLongListFilter(
      Some(statusActionLimits.challengeIds),
      "sa.challenge_id"
    )(getCurrentConjunction(whereClause))
    whereClause ++= this.getLongListFilter(Some(statusActionLimits.taskIds), "sa.task_id")(
      getCurrentConjunction(whereClause)
    )
    whereClause ++= this.getIntListFilter(Some(statusActionLimits.newStatuses), "sa.status")(
      getCurrentConjunction(whereClause)
    )
    whereClause ++= this.getIntListFilter(Some(statusActionLimits.oldStatuses), "sa.old_status")(
      getCurrentConjunction(whereClause)
    )
    whereClause ++= this.getDateClause(
      "sa.created",
      statusActionLimits.startDate,
      statusActionLimits.endDate
    )(getCurrentConjunction(whereClause))

    val query =
      s"""SELECT sa.*, u.name, p.name, c.name
         |FROM status_actions sa
         |INNER JOIN users u ON u.osm_id = sa.osm_user_id
         |INNER JOIN projects p ON p.id = sa.project_id
         |INNER JOIN challenges c ON c.id = sa.challenge_id
         | ${if (whereClause.nonEmpty) {
           s"WHERE ${whereClause.toString}"
         } else {
           ""
         }}
         |ORDER BY created DESC
         |LIMIT ${sqlLimit(limit)} OFFSET $offset""".stripMargin
    db.withConnection { implicit c =>
      sqlWithParameters(query, parameters).as(this.parser.*)
    }
  }

  private def getCurrentConjunction(clause: StringBuilder) =
    if (clause.nonEmpty) {
      Some(AND())
    } else {
      None
    }

  def getStatusSummary(
      user: User,
      statusActionLimits: StatusActionLimits,
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0
  ): List[DailyStatusActionSummary] = {
    val parameters  = new ListBuffer[NamedParameter]()
    val whereClause = new StringBuilder()
    whereClause ++= this.getLongListFilter(Some(statusActionLimits.osmUserIds), "sa.osm_user_id")(
      getCurrentConjunction(whereClause)
    )
    whereClause ++= this.getLongListFilter(Some(statusActionLimits.projectIds), "sa.project_id")(
      getCurrentConjunction(whereClause)
    )
    whereClause ++= this.getLongListFilter(
      Some(statusActionLimits.challengeIds),
      "sa.challenge_id"
    )(getCurrentConjunction(whereClause))

    val query =
      s"""SELECT DATE_TRUNC('day', sa.created) AS daily, sa.osm_user_id, u.name,
         |	  COUNT(sa.status) FILTER (where sa.status = 1) AS fixed,
         |    MAX(sa.created) FILTER (where sa.status = 1) AS lastFixed,
         |    COUNT(sa.status) FILTER (where sa.status = 2) AS falsePositive,
         |    MAX(sa.created) FILTER (where sa.status = 2) AS lastFalsePositive,
         |    COUNT(sa.status) FILTER (where sa.status = 3) AS skipped,
         |    MAX(sa.created) FILTER (where sa.status = 3) AS lastSkipped,
         |    COUNT(sa.status) FILTER (where sa.status = 5) AS alreadyFixed,
         |    MAX(sa.created) FILTER (where sa.status = 5) AS lastAlreadyFixed,
         |    COUNT(sa.status) FILTER (where sa.status = 6) AS tooHard,
         |    MAX(sa.created) FILTER (where sa.status = 6) AS lastTooHard,
         |    COUNT(sa.status) FILTER (where sa.status = 7) AS answered,
         |    MAX(sa.created) FILTER (where sa.status = 7) AS lastAnswered
         |FROM status_actions sa
         |INNER JOIN users u ON u.osm_id = sa.osm_user_id
         | ${if (whereClause.nonEmpty) {
           s"WHERE ${whereClause.toString}"
         } else {
           ""
         }}
         |GROUP BY daily, sa.osm_user_id, u.name
         |ORDER BY daily DESC
         |LIMIT ${sqlLimit(limit)} OFFSET $offset""".stripMargin
    db.withConnection { implicit c =>
      sqlWithParameters(query, parameters).as(this.dailyParser.*)
    }
  }
}
