// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.joda.time.DateTime
import org.maproulette.data.{ItemType, BundleType}
import play.api.libs.json.{Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import play.api.libs.json._

case class Bundle(
    override val id: Long,
    val owner: Long,
    override val name: String = "",
    override val description: Option[String] = None,
    override val created: DateTime = DateTime.now(),
    override val modified: DateTime = DateTime.now()
) extends BaseObject[Long] {
  override val itemType: ItemType = BundleType()
}

object Bundle {
  implicit val bundleWrites: Writes[Bundle] = Json.writes[Bundle]
  implicit val bundleReads: Reads[Bundle]   = Json.reads[Bundle]

  val KEY = "bundles"
}

/**
  * TaskBundles serve as a simple, arbitrary grouping mechanism for tasks
  *
  * @author nrotstan
  */
case class TaskBundle(bundleId: Long, ownerId: Long, taskIds: List[Long], tasks: Option[List[Task]])
    extends DefaultWrites
object TaskBundle {
  implicit val taskBundleWrites: Writes[TaskBundle] = Json.writes[TaskBundle]
  implicit val taskBundleReads: Reads[TaskBundle]   = Json.reads[TaskBundle]
}
