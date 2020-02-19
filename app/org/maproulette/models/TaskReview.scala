// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.joda.time.DateTime
import play.api.libs.json.{DefaultWrites, Json, Reads, Writes}
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
  implicit val reviewWrites: Writes[TaskReview] = Json.writes[TaskReview]
  implicit val reviewReads: Reads[TaskReview]   = Json.reads[TaskReview]
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
    tooHard: Int
)
object ReviewMetrics {
  implicit val reviewMetricsWrites = Json.writes[ReviewMetrics]
}
