// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.joda.time.DateTime
import play.api.libs.json.{Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

/**
  * A comment can be associated to a Task, a comment contains the osm user that made the comment,
  * when it was created, the Task it is associated with, the actual comment and potentially the
  * action that was associated with the comment.
  *
  * @author cuthbertm
  */
case class Comment(
    id: Long,
    osm_id: Long,
    osm_username: String,
    taskId: Long,
    challengeId: Long,
    projectId: Long,
    created: DateTime,
    comment: String,
    actionId: Option[Long] = None
)

object Comment {
  implicit val commentWrites: Writes[Comment] = Json.writes[Comment]
  implicit val commentReads: Reads[Comment]   = Json.reads[Comment]
}
