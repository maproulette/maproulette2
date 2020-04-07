/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.provider.websockets

import org.joda.time.DateTime
import org.maproulette.framework.model.{TaskWithReview, User}
import play.api.libs.json._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import org.maproulette.models.Task

/**
  * Defines case classes representing the various kinds of messages to be
  * transmitted via websocket, as well as helper methods for easily and
  * correctly constructing each kind of message
  *
  * @author nrotstan
  */
object WebSocketMessages {

  sealed trait Message {
    def messageType: String
  }

  // Client messages and data representations
  case class ClientMessage(messageType: String, meta: Option[JsValue], data: Option[JsValue])
      extends Message

  case class SubscriptionData(subscriptionName: String)

  case class PingMessage(messageType: String)

  // Server-generated messages and data representations
  case class ServerMeta(subscriptionName: Option[String], created: DateTime = DateTime.now())

  sealed trait ServerMessage extends Message {
    val meta: ServerMeta
  }

  case class PongMessage(messageType: String, meta: ServerMeta) extends ServerMessage

  case class UserSummary(userId: Long, displayName: String, avatarURL: String)

  case class NotificationData(userId: Long, notificationType: Int)

  case class NotificationMessage(messageType: String, data: NotificationData, meta: ServerMeta)
      extends ServerMessage

  case class ReviewData(taskWithReview: TaskWithReview)

  case class ReviewMessage(messageType: String, data: ReviewData, meta: ServerMeta)
      extends ServerMessage

  case class TaskAction(task: Task, byUser: Option[UserSummary])

  case class TaskMessage(messageType: String, data: TaskAction, meta: ServerMeta)
      extends ServerMessage

  // Public helper methods for creation of individual messages and data objects
  def pong(): PongMessage = PongMessage("pong", ServerMeta(None))

  def notificationNew(data: NotificationData): NotificationMessage =
    createNotificationMessage("notification-new", data)

  def reviewNew(data: ReviewData): ReviewMessage = createReviewMessage("review-new", data)

  def reviewClaimed(data: ReviewData): ReviewMessage = createReviewMessage("review-claimed", data)

  def reviewUpdate(data: ReviewData): ReviewMessage = createReviewMessage("review-update", data)

  def taskClaimed(taskData: Task, userData: Option[UserSummary]): List[ServerMessage] =
    createTaskMessage("task-claimed", taskData, userData)

  def taskReleased(taskData: Task, userData: Option[UserSummary]): List[ServerMessage] =
    createTaskMessage("task-released", taskData, userData)

  def taskUpdate(taskData: Task, userData: Option[UserSummary]): List[ServerMessage] =
    createTaskMessage("task-update", taskData, userData)

  def userSummary(user: User): UserSummary =
    UserSummary(user.id, user.osmProfile.displayName, user.osmProfile.avatarURL)

  // private helper methods
  private def createNotificationMessage(
      messageType: String,
      data: NotificationData
  ): NotificationMessage = {
    NotificationMessage(
      messageType,
      data,
      ServerMeta(Some(WebSocketMessages.SUBSCRIPTION_USER + s"_${data.userId}"))
    )
  }

  private def createReviewMessage(messageType: String, data: ReviewData): ReviewMessage = {
    ReviewMessage(messageType, data, ServerMeta(Some(WebSocketMessages.SUBSCRIPTION_REVIEWS)))
  }

  private def createTaskMessage(
      messageType: String,
      taskData: Task,
      userData: Option[UserSummary]
  ): List[ServerMessage] = {
    val data = TaskAction(taskData, userData)

    // Create one message for subscribers to all tasks and one for subscribers
    // to just challenge-specific tasks
    List[WebSocketMessages.ServerMessage](
      TaskMessage(messageType, data, ServerMeta(Some(WebSocketMessages.SUBSCRIPTION_TASKS))),
      TaskMessage(
        messageType,
        data,
        ServerMeta(Some(WebSocketMessages.SUBSCRIPTION_CHALLENGE_TASKS + s"_${data.task.parent}"))
      )
    )
  }

  private def createChallengeTaskMessage(messageType: String, data: TaskAction): TaskMessage = {
    TaskMessage(
      messageType,
      data,
      ServerMeta(Some(WebSocketMessages.SUBSCRIPTION_CHALLENGE_TASKS + s"_${data.task.parent}"))
    )
  }

  // Reads for client messages
  implicit val clientMessageReads: Reads[ClientMessage]       = Json.reads[ClientMessage]
  implicit val subscriptionDataReads: Reads[SubscriptionData] = Json.reads[SubscriptionData]
  implicit val pingMessageReads: Reads[PingMessage]           = Json.reads[PingMessage]

  // Writes for server-generated messages
  implicit val serverMetaWrites: Writes[ServerMeta]             = Json.writes[ServerMeta]
  implicit val pongMessageWrites: Writes[PongMessage]           = Json.writes[PongMessage]
  implicit val UserSummaryWrites: Writes[UserSummary]           = Json.writes[UserSummary]
  implicit val notificationDataWrites: Writes[NotificationData] = Json.writes[NotificationData]
  implicit val notificationMessageWrites: Writes[NotificationMessage] =
    Json.writes[NotificationMessage]
  implicit val reviewDataWrites: Writes[ReviewData]       = Json.writes[ReviewData]
  implicit val reviewMessageWrites: Writes[ReviewMessage] = Json.writes[ReviewMessage]
  implicit val taskActionWrites: Writes[TaskAction]       = Json.writes[TaskAction]
  implicit val taskMessageWrites: Writes[TaskMessage]     = Json.writes[TaskMessage]

  // Available subscription types
  val SUBSCRIPTION_USER            = "user" // expected to be accompanied by user id
  val SUBSCRIPTION_USERS           = "users"
  val SUBSCRIPTION_REVIEWS         = "reviews"
  val SUBSCRIPTION_TASKS           = "tasks"
  val SUBSCRIPTION_CHALLENGE_TASKS = "challengeTasks" // expected to be accompanied by challenge id
}
