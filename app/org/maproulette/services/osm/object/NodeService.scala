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
  * Service extending from the ObjectService that retrieves and caches nodes
  *
  * @author mcuthbert
  */
@Singleton
class NodeService @Inject() (override val ws:WSClient, override val config:Config) extends ObjectService[VersionedNode] {
  val cache = new CacheStorage[Long, VersionedObjects[VersionedNode]](cacheExpiry = ObjectService.DEFAULT_CACHE_EXPIRY)

  def get(ids:List[Long]) : Future[List[VersionedNode]] = _get(ids, OSMType.NODE)

  override protected def _createVersionedObject(elem:Node, id: Long, visible:Boolean, version: Int, changeset: Int,
                                                timestamp: DateTime, user: String, uid: Long, tags: Map[String, String]): VersionedNode = {
    VersionedNode(
      s"Node_$id",
      id,
      visible,
      version,
      changeset,
      timestamp,
      user,
      uid,
      tags,
      (elem \ "@lat").text.toDouble,
      (elem \ "@lon").text.toDouble
    )
  }
}
