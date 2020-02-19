package org.maproulette.utils

import org.maproulette.models._
import org.maproulette.models.utils.ChallengeReads
import org.maproulette.session.{Group, User}

/**
  * @author mcuthbert
  */
trait Readers extends ChallengeReads {
  // User Readers
  implicit val tokenReads     = User.tokenReads
  implicit val settingsReads  = User.settingsReads
  implicit val userGroupReads = User.userGroupReads
  implicit val locationReads  = User.locationReads
  implicit val osmReads       = User.osmReads
  // Group Readers
  implicit val groupReads = Group.groupReads
  // Challenge Readers
  implicit val answerReads = Challenge.answerReads
  // Point Readers
  implicit val pointReads          = ClusteredPoint.pointReads
  implicit val clusteredPointReads = ClusteredPoint.clusteredPointReads
  // Comment Readers
  implicit val commentReads = Comment.commentReads
  // Project Readers
  implicit val projectReads = Project.projectReads
  // Tag Readers
  implicit val tagReads = Tag.tagReads
  // Task Readers
  implicit val taskReads           = Task.TaskFormat
  implicit val taskClusterReads    = TaskCluster.taskClusterReads
  implicit val taskLogEntryReads   = TaskLogEntry.taskLogEntryReads
  implicit val taskReviewReads     = TaskReview.reviewReads
  implicit val taskWithReviewReads = TaskWithReview.taskWithReviewReads
  // UserNotification Readers
  implicit val userNotificationReads = UserNotification.notificationReads
  implicit val userNotificationSubscriptionsReads =
    NotificationSubscriptions.notificationSubscriptionReads
}
