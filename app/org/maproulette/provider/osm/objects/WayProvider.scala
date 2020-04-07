/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.provider.osm.objects

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.BasicCache
import org.maproulette.provider.osm.OSMType
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.xml.Node

/**
  * Service extending from the ObjectService that retrieves and caches ways
  *
  * @author mcuthbert
  */
@Singleton
class WayProvider @Inject() (override val ws: WSClient, override val config: Config)
    extends ObjectProvider[VersionedWay] {
  val cache = new BasicCache[Long, VersionedObjects[VersionedWay]](config)

  def get(ids: List[Long]): Future[List[VersionedWay]] = getFromType(ids, OSMType.WAY)

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
  ): VersionedWay = {
    VersionedWay(
      s"Node_$id",
      id,
      visible,
      version,
      changeset,
      timestamp,
      user,
      uid,
      tags,
      (elem \ "nd").map(v => (v \ "@ref").text.toLong).toList
    )
  }
}
