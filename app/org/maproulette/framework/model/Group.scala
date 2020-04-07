/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.framework.model

import org.maproulette.cache.CacheObject
import org.maproulette.framework.psql.CommonField
import play.api.libs.json.{Json, Reads, Writes}

/**
  * @author cuthbertm
  */
case class Group(
    override val id: Long,
    override val name: String,
    projectId: Long,
    groupType: Int
) extends CacheObject[Long]

object Group extends CommonField {
  implicit val writes: Writes[Group] = Json.writes[Group]
  implicit val reads: Reads[Group]   = Json.reads[Group]

  val FIELD_GROUP_TYPE     = "group_type"
  val FIELD_PROJECT_ID     = "project_id"
  val FIELD_UG_OSM_USER_ID = "osm_user_id"
  val FIELD_UG_GROUP_ID    = "group_id"

  val TYPE_SUPER_USER   = -1
  val TYPE_ADMIN        = 1
  val TYPE_WRITE_ACCESS = 2
  val TYPE_READ_ONLY    = 3
}
