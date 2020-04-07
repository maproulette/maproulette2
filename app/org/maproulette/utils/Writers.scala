/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.utils

import org.maproulette.framework.model.{
  Comment,
  Group,
  Location,
  OSMProfile,
  Project,
  TaskReview,
  TaskWithReview,
  User,
  UserSettings
}
import org.maproulette.models._
import org.maproulette.models.utils.ChallengeWrites
import org.maproulette.session._
import play.api.libs.json.Writes
import play.api.libs.oauth.RequestToken

/**
  * @author mcuthbert
  */
trait Writers extends ChallengeWrites {
  // User Writers
  implicit val tokenWrites: Writes[RequestToken]    = User.tokenWrites
  implicit val settingsWrites: Writes[UserSettings] = User.settingsWrites
  implicit val userGroupWrites: Writes[Group]       = User.userGroupWrites
  implicit val locationWrites: Writes[Location]     = User.locationWrites
  implicit val osmWrites: Writes[OSMProfile]        = User.osmWrites
  // Group Writers
  implicit val groupWrites: Writes[Group] = Group.writes
  // Point Writers
  implicit val pointWrites: Writes[Point]                   = ClusteredPoint.pointWrites
  implicit val clusteredPointWrites: Writes[ClusteredPoint] = ClusteredPoint.clusteredPointWrites
  // Comment Writers
  implicit val commentWrites: Writes[Comment] = Comment.writes
  // Project Writers
  implicit val projectWrites: Writes[Project] = Project.writes
  // Tag Writers
  implicit val tagWrites: Writes[Tag] = Tag.tagWrites
  // Task Writers
  implicit val taskWrites                                   = Task.TaskFormat
  implicit val taskClusterWrites: Writes[TaskCluster]       = TaskCluster.taskClusterWrites
  implicit val taskLogEntryWrites: Writes[TaskLogEntry]     = TaskLogEntry.taskLogEntryWrites
  implicit val taskReviewWrites: Writes[TaskReview]         = TaskReview.writes
  implicit val taskWithReviewWrites: Writes[TaskWithReview] = TaskWithReview.taskWithReviewWrites
  // UserNotification Writers
  implicit val userNotificationWrites: Writes[UserNotification] =
    UserNotification.notificationWrites
  implicit val userNotificationSubscriptionsWrites: Writes[NotificationSubscriptions] =
    NotificationSubscriptions.notificationSubscriptionWrites
}
