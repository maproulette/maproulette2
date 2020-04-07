/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import java.sql.Connection

import anorm.SqlParser.get
import anorm.{RowParser, ~}
import javax.inject.{Inject, Singleton}
import org.maproulette.framework.psql.Query
import org.maproulette.models.Task
import play.api.db.Database

/**
  * @author mcuthbert
  */
@Singleton
class TaskReviewRepository @Inject() (override val db: Database) extends RepositoryMixin {
  def getTaskReviewCounts(query: Query)(implicit c: Option[Connection] = None): Map[String, Int] = {
    this.withMRTransaction { implicit c =>
      val reviewCountsParser: RowParser[Map[String, Int]] = {
        get[Int]("total") ~
          get[Int]("approvedCount") ~
          get[Int]("rejectedCount") ~
          get[Int]("assistedCount") ~
          get[Int]("disputedCount") ~
          get[Int]("requestedCount") ~
          get[Double]("total_review_time") ~
          get[Int]("tasks_with_review_time") map {
          case total ~ approvedCount ~ rejectedCount ~ assistedCount ~ disputedCount ~
                requestedCount ~ totalReviewTime ~ tasksWithReviewTime => {
            Map(
              "total"     -> total,
              "approved"  -> approvedCount,
              "rejected"  -> rejectedCount,
              "assisted"  -> assistedCount,
              "disputed"  -> disputedCount,
              "requested" -> requestedCount,
              "avgReviewTime" -> (if (tasksWithReviewTime > 0)
                                    (totalReviewTime / tasksWithReviewTime).toInt
                                  else 0)
            )
          }
        }
      }

      query.build(s"""
                       |SELECT count(*) as total,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_APPROVED} then 1 else 0 end), 0) approvedCount,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_REJECTED} then 1 else 0 end), 0) rejectedCount,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_ASSISTED} then 1 else 0 end), 0) assistedCount,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_DISPUTED} then 1 else 0 end), 0) disputedCount,
                       |COALESCE(sum(case when review_status = ${Task.REVIEW_STATUS_REQUESTED} then 1 else 0 end), 0) requestedCount,
                       |COALESCE(SUM(CASE WHEN (review_started_at IS NOT NULL AND
                       |                        reviewed_at IS NOT NULL)
                       |                  THEN (EXTRACT(EPOCH FROM (reviewed_at - review_started_at)) * 1000)
                       |                  ELSE 0 END), 0) total_review_time,
                       |COALESCE(SUM(CASE WHEN (review_started_at IS NOT NULL AND
                       |                        reviewed_at IS NOT NULL)
                       |                  THEN 1 ELSE 0 END), 0) tasks_with_review_time
                       |FROM task_review
        """.stripMargin).as(reviewCountsParser.single)
    }
  }
}
