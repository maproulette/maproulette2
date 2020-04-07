/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.model

/**
  * @author mcuthbert
  */
object UserMetrics {
  val FIELD_USER_ID                    = "user_id"
  val FIELD_SCORE                      = "score"
  val FIELD_TOTAL_FIXED                = "total_fixed"
  val FIELD_TOTAL_FALSE_POSITIVE       = "total_false_positive"
  val FIELD_TOTAL_ALREADY_FIXED        = "total_already_fixed"
  val FIELD_TOTAL_TOO_HARD             = "total_too_hard"
  val FIELD_TOTAL_SKIPPED              = "total_skipped"
  val FIELD_INITIAL_REJECTED           = "initial_rejected"
  val FIELD_INITIAL_APPROVED           = "initial_approved"
  val FIELD_INITIAL_ASSISTED           = "initial_assisted"
  val FIELD_TOTAL_REJECTED             = "total_rejected"
  val FIELD_TOTAL_APPROVED             = "total_approved"
  val FIELD_TOTAL_ASSISTED             = "total_assisted"
  val FIELD_TOTAL_DISPUTED_AS_MAPPER   = "total_disputed_as_mapper"
  val FIELD_TOTAL_DISPUTED_AS_REVIEWER = "total_disputed_as_reviewer"
  val FIELD_TOTAL_TIME_SPENT           = "total_time_spent"
  val FIELD_TASKS_WITH_TIME            = "tasks_with_time"
  val FIELD_TOTAL_REVIEW_TIME          = "total_review_time"
  val FIELD_TASKS_WITH_REVIEW_TIME     = "tasks_with_review_time"
}
