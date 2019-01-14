package org.maproulette.services.osm.`object`

import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.CacheStorage
import org.maproulette.services.osm.OSMType
import play.api.libs.ws.WSClient

import scala.concurrent.Future
import scala.xml.Node

/**
  * Service extending from the ObjectService that retrieves and caches ways
  *
  * @author mcuthbert
  */
@Singleton
class WayService @Inject() (override val ws:WSClient, override val config:Config) extends ObjectService[VersionedWay] {
  val cache = new CacheStorage[Long, VersionedObjects[VersionedWay]](cacheExpiry = ObjectService.DEFAULT_CACHE_EXPIRY)

  def get(ids:List[Long]) : Future[List[VersionedWay]] = _get(ids, OSMType.WAY)

  override protected def _createVersionedObject(elem:Node, id: Long, visible:Boolean, version: Int, changeset: Int,
                                                timestamp: DateTime, user: String, uid: Long, tags: Map[String, String]): VersionedWay = {
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
