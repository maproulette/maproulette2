/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.controllers.api

import javax.inject.Inject
import org.maproulette.Config
import org.maproulette.data.ActionManager
import org.maproulette.framework.service.ServiceManager
import org.maproulette.models.TaskLogEntry
import org.maproulette.models.dal._
import org.maproulette.provider.websockets.WebSocketProvider
import org.maproulette.provider.osm.ChangesetProvider
import org.maproulette.session.SessionManager
import org.maproulette.utils.Utils
import play.api.libs.json._
import play.api.libs.ws.WSClient
import play.api.mvc._

/**
  * TaskHistoryController is responsible for fetching the history of a task.
  *
  * @author krotstan
  */
class TaskHistoryController @Inject() (
    override val sessionManager: SessionManager,
    override val actionManager: ActionManager,
    override val dal: TaskDAL,
    override val tagDAL: TagDAL,
    taskHistoryDAL: TaskHistoryDAL,
    serviceManager: ServiceManager,
    dalManager: DALManager,
    wsClient: WSClient,
    webSocketProvider: WebSocketProvider,
    config: Config,
    components: ControllerComponents,
    changeService: ChangesetProvider,
    override val bodyParsers: PlayBodyParsers
) extends TaskController(
      sessionManager,
      actionManager,
      dal,
      tagDAL,
      serviceManager,
      dalManager,
      wsClient,
      webSocketProvider,
      config,
      components,
      changeService,
      bodyParsers
    ) {

  /**
    * Gets the history for a task. This includes commments, status_actions, and review_actions.
    *
    * @param taskId      The id of the task being viewed
    * @return
    */
  def getTaskHistoryLog(taskId: Long): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(_insertExtraJSON(this.taskHistoryDAL.getTaskHistoryLog(taskId)))
    }
  }

  /**
    * Fetches and inserts usernames for 'osm_user_id', 'reviewRequestedBy' and 'reviewBy'
    */
  private def _insertExtraJSON(entries: List[TaskLogEntry]): JsValue = {
    if (entries.isEmpty) {
      Json.toJson(List[JsValue]())
    } else {
      val users = Some(
        this.serviceManager.user
          .retrieveListById(entries.map(t => t.user.getOrElse(0).toLong))
          .map(u => u.id -> Json.obj("username" -> u.name, "id" -> u.id))
          .toMap
      )

      val mappers = Some(
        this.serviceManager.user
          .retrieveListById(entries.map(t => t.reviewRequestedBy.getOrElse(0).toLong))
          .map(u => u.id -> Json.obj("username" -> u.name, "id" -> u.id))
          .toMap
      )

      val reviewers = Some(
        this.serviceManager.user
          .retrieveListById(entries.map(t => t.reviewedBy.getOrElse(0).toLong))
          .map(u => u.id -> Json.obj("username" -> u.name, "id" -> u.id))
          .toMap
      )

      val jsonList = entries.map { entry =>
        var updated = Json.toJson(entry)
        if (entry.user.getOrElse(0) != 0) {
          val usersJson = Json.toJson(users.get(entry.user.get.toLong)).as[JsObject]
          updated = Utils.insertIntoJson(updated, "user", usersJson, true)
        }
        if (entry.reviewRequestedBy.getOrElse(0) != 0) {
          val mapperJson = Json.toJson(mappers.get(entry.reviewRequestedBy.get.toLong)).as[JsObject]
          updated = Utils.insertIntoJson(updated, "reviewRequestedBy", mapperJson, true)
        }
        if (entry.reviewedBy.getOrElse(0) != 0) {
          val reviewerJson = Json.toJson(reviewers.get(entry.reviewedBy.get.toLong)).as[JsObject]
          updated = Utils.insertIntoJson(updated, "reviewedBy", reviewerJson, true)
        }

        updated
      }
      Json.toJson(jsonList)
    }
  }
}
