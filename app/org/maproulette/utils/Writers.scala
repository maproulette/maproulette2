package org.maproulette.utils

import org.maproulette.models._
import org.maproulette.models.utils.ChallengeWrites
import org.maproulette.session.{Group, User}

/**
  * @author mcuthbert
  */
trait Writers extends ChallengeWrites {
  // User Writers
  implicit val tokenWrites     = User.tokenWrites
  implicit val settingsWrites  = User.settingsWrites
  implicit val userGroupWrites = User.userGroupWrites
  implicit val locationWrites  = User.locationWrites
  implicit val osmWrites       = User.osmWrites
  // Group Writers
  implicit val groupWrites = Group.groupWrites
  // Challenge Writers
  implicit val answerWrites = Challenge.answerWrites
  // Point Writers
  implicit val pointWrites          = ClusteredPoint.pointWrites
  implicit val clusteredPointWrites = ClusteredPoint.clusteredPointWrites
  // Comment Writers
  implicit val commentWrites = Comment.commentWrites
  // Project Writers
  implicit val projectWrites = Project.projectWrites
  // Tag Writers
  implicit val tagWrites = Tag.tagWrites
  // Task Writers
  implicit val taskWrites           = Task.TaskFormat
  implicit val taskClusterWrites    = TaskCluster.taskClusterWrites
  implicit val taskLogEntryWrites   = TaskLogEntry.taskLogEntryWrites
  implicit val taskReviewWrites     = TaskReview.reviewWrites
  implicit val taskWithReviewWrites = TaskWithReview.taskWithReviewWrites
  // UserNotification Writers
  implicit val userNotificationWrites = UserNotification.notificationWrites
  implicit val userNotificationSubscriptionsWrites =
    NotificationSubscriptions.notificationSubscriptionWrites
}
