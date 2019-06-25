// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.joda.time.DateTime
import org.maproulette.data.{ItemType, TagType}
import play.api.libs.json.{Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

/**
  * Tags sit outside of the object hierarchy and have no parent or children objects associated it.
  * It simply has a many to one mapping between tags and tasks. This allows tasks to be easily
  * searched for and organized. Helping people find tasks related to what interests them.
  *
  * @author cuthbertm
  */
case class Tag(override val id: Long,
               override val name: String,
               override val description: Option[String] = None,
               override val created: DateTime = DateTime.now(),
               override val modified: DateTime = DateTime.now(),
               tagType: String = "challenges") extends BaseObject[Long] {
  override val itemType: ItemType = TagType()
}

object Tag {
  implicit val tagWrites: Writes[Tag] = Json.writes[Tag]
  implicit val tagReads: Reads[Tag] = Json.reads[Tag]

  val KEY = "tags"
}
