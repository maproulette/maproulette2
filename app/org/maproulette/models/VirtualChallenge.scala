/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.models

import org.joda.time.DateTime
import org.maproulette.data.{ItemType, VirtualChallengeType}
import org.maproulette.session.SearchParameters
import play.api.libs.json.{DefaultWrites, Json, Reads, Writes}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._

/**
  * @author mcuthbert
  */
case class VirtualChallenge(
    override val id: Long,
    override val name: String,
    override val created: DateTime,
    override val modified: DateTime,
    override val description: Option[String] = None,
    ownerId: Long,
    searchParameters: SearchParameters,
    expiry: DateTime,
    taskIdList: Option[List[Long]] = None
) extends BaseObject[Long]
    with DefaultWrites {

  override val itemType: ItemType = VirtualChallengeType()

  def isExpired: Boolean = DateTime.now().isAfter(expiry)
}

object VirtualChallenge {
  implicit val virtualChallengeWrites: Writes[VirtualChallenge] = Json.writes[VirtualChallenge]
  implicit val virtualChallengeReads: Reads[VirtualChallenge]   = Json.reads[VirtualChallenge]
}
