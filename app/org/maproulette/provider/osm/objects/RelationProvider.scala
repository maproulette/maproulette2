// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.services.osm.objects

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.BasicCache
import org.maproulette.services.osm.OSMType
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.xml.Node

/**
  * Service extending from the ObjectService that retrieves and caches relations
  *
  * @author mcuthbert
  */
@Singleton
class RelationProvider @Inject() (override val ws: WSClient, override val config: Config)
    extends ObjectProvider[VersionedRelation] {
  val cache = new BasicCache[Long, VersionedObjects[VersionedRelation]](config)

  def get(ids: List[Long]): Future[List[VersionedRelation]] = getFromType(ids, OSMType.RELATION)

  override protected def createVersionedObjectFromXML(
      elem: Node,
      id: Long,
      visible: Boolean,
      version: Int,
      changeset: Int,
      timestamp: DateTime,
      user: String,
      uid: Long,
      tags: Map[String, String]
  ): VersionedRelation = {
    VersionedRelation(
      s"Node_$id",
      id,
      visible,
      version,
      changeset,
      timestamp,
      user,
      uid,
      tags,
      (elem \ "member")
        .map(elem => {
          RelationMember((elem \ "@type").text, (elem \ "@ref").text.toLong, (elem \ "@role").text)
        })
        .toList
    )
  }
}
