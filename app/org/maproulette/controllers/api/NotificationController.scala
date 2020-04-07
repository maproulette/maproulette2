/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.controllers.api

import javax.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.maproulette.exception.{InvalidException, NotFoundException, StatusMessage}
import org.maproulette.models.dal.{NotificationDAL}
import org.maproulette.session.SessionManager
import org.maproulette.models.{NotificationSubscriptions}
import org.maproulette.utils.{Crypto, Utils}
import play.api.libs.json._
import play.api.mvc._

import scala.concurrent.Promise
import scala.util.{Failure, Success}

/**
  * @author nrotstan
  */
class NotificationController @Inject() (
    notificationDAL: NotificationDAL,
    sessionManager: SessionManager,
    components: ControllerComponents,
    bodyParsers: PlayBodyParsers,
    crypto: Crypto
) extends AbstractController(components)
    with DefaultWrites {

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val notificationSubscriptionReads =
    NotificationSubscriptions.notificationSubscriptionReads
  implicit val notificationSubscriptionWrites =
    NotificationSubscriptions.notificationSubscriptionWrites

  def getUserNotifications(
      userId: Long,
      limit: Int,
      page: Int,
      sort: String,
      order: String,
      notificationType: Option[Int] = None,
      isRead: Option[Int] = None,
      fromUsername: Option[String] = None,
      challengeId: Option[Long] = None
  ): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      // Routes don't allow Boolean options, so convert isRead from Int option (zero false, non-zero true)
      val readFilter: Option[Boolean] = isRead match {
        case Some(value) => Some(value != 0)
        case None        => None
      }

      Ok(
        Json.toJson(
          this.notificationDAL.getUserNotifications(
            userId,
            user,
            limit,
            page * limit,
            sort,
            order,
            notificationType,
            readFilter,
            fromUsername,
            challengeId
          )
        )
      )
    }
  }

  def markNotificationsRead(userId: Long, notificationIds: String): Action[AnyContent] =
    Action.async { implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        if (!StringUtils.isEmpty(notificationIds)) {
          val parsedNotificationIds = Utils.split(notificationIds).map(_.toLong)
          this.notificationDAL.markNotificationsRead(userId, user, parsedNotificationIds)
        }
        Ok(Json.toJson(StatusMessage("OK", JsString(s"Notifications marked as read"))))
      }
    }

  def deleteNotifications(userId: Long, notificationIds: String): Action[AnyContent] =
    Action.async { implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        if (!StringUtils.isEmpty(notificationIds)) {
          val parsedNotificationIds = Utils.split(notificationIds).map(_.toLong)
          this.notificationDAL.deleteNotifications(userId, user, parsedNotificationIds)
        }
        Ok(Json.toJson(StatusMessage("OK", JsString(s"Notifications deleted"))))
      }
    }

  def getNotificationSubscriptions(userId: Long): Action[AnyContent] = Action.async {
    implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        Ok(Json.toJson(this.notificationDAL.getNotificationSubscriptions(userId, user)))
      }
  }

  def updateNotificationSubscriptions(userId: Long): Action[JsValue] =
    Action.async(bodyParsers.json) { implicit request =>
      this.sessionManager.authenticatedRequest { implicit user =>
        notificationDAL.updateNotificationSubscriptions(
          userId,
          user,
          request.body.as[NotificationSubscriptions]
        )
        Ok(Json.toJson(StatusMessage("OK", JsString(s"Subscriptions updated"))))
      }
    }
}
