/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.model

import org.joda.time.DateTime
import org.maproulette.models.Task
import play.api.libs.json.{Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

case class TaskReview(
    id: Long,
    taskId: Long,
    reviewStatus: Option[Int],
    challengeName: Option[String],
    reviewRequestedBy: Option[Long],
    reviewRequestedByUsername: Option[String],
    reviewedBy: Option[Long],
    reviewedByUsername: Option[String],
    reviewedAt: Option[DateTime],
    reviewStartedAt: Option[DateTime],
    reviewClaimedBy: Option[Long],
    reviewClaimedByUsername: Option[String],
    reviewClaimedAt: Option[DateTime]
)
object TaskReview {
  implicit val writes: Writes[TaskReview] = Json.writes[TaskReview]
  implicit val reads: Reads[TaskReview]   = Json.reads[TaskReview]

  val FIELD_TASK_ID             = "task_id"
  val FIELD_REVIEW_STATUS       = "review_status"
  val FIELD_REVIEW_REQUESTED_BY = "review_requested_by"
  val FIELD_REVIEWED_BY         = "reviewed_by"
  val FIELD_REVIEWED_AT         = "reviewed_at"
  val FIELD_REVIEW_CLAIMED_AT   = "review_claimed_at"
  val FIELD_REVIEW_CLAIMED_BY   = "review_claimed_by"
  val FIELD_REVIEW_STARTED_AT   = "review_started_at"
}

case class TaskWithReview(task: Task, review: TaskReview)
object TaskWithReview {
  implicit val taskWithReviewWrites: Writes[TaskWithReview] = Json.writes[TaskWithReview]
  implicit val taskWithReviewReads: Reads[TaskWithReview]   = Json.reads[TaskWithReview]
}

case class ReviewMetrics(
    total: Int,
    reviewRequested: Int,
    reviewApproved: Int,
    reviewRejected: Int,
    reviewAssisted: Int,
    reviewDisputed: Int,
    fixed: Int,
    falsePositive: Int,
    skipped: Int,
    alreadyFixed: Int,
    tooHard: Int,
    avgReviewTime: Double
)
object ReviewMetrics {
  implicit val reviewMetricsWrites = Json.writes[ReviewMetrics]
}
