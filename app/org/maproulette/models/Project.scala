// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models

import org.joda.time.DateTime
import org.maproulette.data.{ItemType, ProjectType}
import org.maproulette.session.{Group, User}
import play.api.libs.json.{Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

/**
  * The project object is the root object of hierarchy, it is built to allow users to have personal
  * domains where they can create their own challenges and have a permissions model that allows
  * users to have and give control over what happens within that domain.
  *
  * @author cuthbertm
  */
case class Project(override val id: Long,
                   owner: Long,
                   override val name: String,
                   override val created: DateTime,
                   override val modified: DateTime,
                   override val description: Option[String] = None,
                   groups: List[Group] = List.empty,
                   enabled: Boolean = false,
                   displayName: Option[String] = None,
                   deleted: Boolean = false,
                   isVirtual: Option[Boolean] = Some(false)) extends BaseObject[Long] {

  override val itemType: ItemType = ProjectType()
}

object Project {
  implicit val groupWrites: Writes[Group] = Json.writes[Group]
  implicit val groupReads: Reads[Group] = Json.reads[Group]
  implicit val projectWrites: Writes[Project] = Json.writes[Project]
  implicit val projectReads: Reads[Project] = Json.reads[Project]

  val KEY_GROUPS = "groups"

  def emptyProject: Project = Project(-1, User.DEFAULT_SUPER_USER_ID, "", DateTime.now(), DateTime.now())
}
