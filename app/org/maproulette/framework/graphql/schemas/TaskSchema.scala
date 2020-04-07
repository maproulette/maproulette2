/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.graphql.schemas

import org.maproulette.models.{MapillaryImage, Task, TaskReviewFields}
import sangria.macros.derive.{ObjectTypeName, deriveObjectType}
import sangria.schema._

/**
  * @author mcuthbert
  */
class TaskSchema {}

object TaskSchema {
  val taskIdArg: Argument[Long] = Argument("taskId", LongType)
}
