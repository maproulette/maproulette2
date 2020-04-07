/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.models

import org.joda.time.DateTime
import play.api.libs.json.{DefaultWrites, Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

/**
  * A user notification represents communication to a user about an event. It
  * can be associated with a task/challenge/project as well as other entities,
  * such as comments or reviews (referenced by targetId).
  *
  * @author nrotstan
  */
case class UserNotification(
    var id: Long,
    var userId: Long,
    var notificationType: Int,
    var created: DateTime = DateTime.now,
    var modified: DateTime = DateTime.now,
    var description: Option[String] = None,
    var fromUsername: Option[String] = None,
    var challengeName: Option[String] = None,
    var isRead: Boolean = false,
    var emailStatus: Int = 0,
    var taskId: Option[Long] = None,
    var challengeId: Option[Long] = None,
    var projectId: Option[Long] = None,
    var targetId: Option[Long] = None,
    var extra: Option[String] = None
)

object UserNotification {
  implicit val notificationWrites: Writes[UserNotification] = Json.writes[UserNotification]
  implicit val notificationReads: Reads[UserNotification]   = Json.reads[UserNotification]

  val NOTIFICATION_TYPE_SYSTEM                   = 0
  val NOTIFICATION_TYPE_SYSTEM_NAME              = "System Message"
  val NOTIFICATION_TYPE_MENTION                  = 1
  val NOTIFICATION_TYPE_MENTION_NAME             = "Comment Mention"
  val NOTIFICATION_TYPE_REVIEW_APPROVED          = 2
  val NOTIFICATION_TYPE_REVIEW_APPROVED_NAME     = "Task Approved"
  val NOTIFICATION_TYPE_REVIEW_REJECTED          = 3
  val NOTIFICATION_TYPE_REVIEW_REJECTED_NAME     = "Revision Requested"
  val NOTIFICATION_TYPE_REVIEW_AGAIN             = 4
  val NOTIFICATION_TYPE_REVIEW_AGAIN_NAME        = "Review Requested"
  val NOTIFICATION_TYPE_CHALLENGE_COMPLETED      = 5
  val NOTIFICATION_TYPE_CHALLENGE_COMPLETED_NAME = "Challenge Completed"
  val notificationTypeMap = Map(
    NOTIFICATION_TYPE_SYSTEM              -> NOTIFICATION_TYPE_SYSTEM_NAME,
    NOTIFICATION_TYPE_MENTION             -> NOTIFICATION_TYPE_MENTION_NAME,
    NOTIFICATION_TYPE_REVIEW_APPROVED     -> NOTIFICATION_TYPE_REVIEW_APPROVED_NAME,
    NOTIFICATION_TYPE_REVIEW_REJECTED     -> NOTIFICATION_TYPE_REVIEW_REJECTED_NAME,
    NOTIFICATION_TYPE_REVIEW_AGAIN        -> NOTIFICATION_TYPE_REVIEW_AGAIN_NAME,
    NOTIFICATION_TYPE_CHALLENGE_COMPLETED -> NOTIFICATION_TYPE_CHALLENGE_COMPLETED_NAME
  )

  val NOTIFICATION_IGNORE          = 0 // ignore notification
  val NOTIFICATION_EMAIL_NONE      = 1 // no email desired
  val NOTIFICATION_EMAIL_IMMEDIATE = 2 // send email immediately
  val NOTIFICATION_EMAIL_DIGEST    = 3 // include in daily digest
  val NOTIFICATION_EMAIL_SENT      = 4 // requested email sent
}

case class NotificationSubscriptions(
    val id: Long,
    val userId: Long,
    val system: Int,
    val mention: Int,
    val reviewApproved: Int,
    val reviewRejected: Int,
    val reviewAgain: Int,
    val challengeCompleted: Int
)
object NotificationSubscriptions {
  implicit val notificationSubscriptionReads: Reads[NotificationSubscriptions] =
    Json.reads[NotificationSubscriptions]
  implicit val notificationSubscriptionWrites: Writes[NotificationSubscriptions] =
    Json.writes[NotificationSubscriptions]
}

case class UserNotificationEmail(
    val id: Long,
    val userId: Long,
    val notificationType: Int,
    val extra: Option[String],
    val created: DateTime,
    val emailStatus: Int
)

case class UserNotificationEmailDigest(
    val userId: Long,
    val notifications: List[UserNotificationEmail]
)
