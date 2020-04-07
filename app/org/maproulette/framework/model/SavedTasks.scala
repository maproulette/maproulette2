/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.model

import org.maproulette.framework.psql.CommonField

/**
  * Fields for the SavedTasks table
  *
  * @author mcuthbert
  */
object SavedTasks extends CommonField {
  val FIELD_USER_ID      = "user_id"
  val FIELD_TASK_ID      = "task_id"
  val FIELD_CHALLENGE_ID = "challenge_id"
}
