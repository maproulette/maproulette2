package org.maproulette.services.osm.`object`

import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.CacheStorage
import org.maproulette.exception.NotFoundException
import org.maproulette.services.osm.OSMType.OSMType
import play.api.libs.ws.WSClient

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import scala.xml.Node

/**
  * Class that handles retrieving and caching OSM Nodes, Ways and Relations
  *
  * @author mcuthbert
  */
trait ObjectService[T<:VersionedObject] {
  implicit val ws:WSClient
  implicit val cache:CacheStorage[Long, VersionedObjects[T]]
  implicit val config:Config

  import scala.concurrent.ExecutionContext.Implicits.global

  private def getObjectFromCache(key:Long) : Option[VersionedObjects[T]] = this.cache.get(key)

  private def addObjectToCache(obj:T) : T = {
    val objects = this.cache.get(obj.id) match {
      case Some(v) => v
      case None => new VersionedObjects[T](obj.id, obj.name)
    }
    objects.put(obj)
    this.cache.addObject(objects)
    obj
  }

  private def getURLPrefix(osmType:OSMType) : String =
    s"${config.getOSMServer}/api/0.6/${osmType.toString.toLowerCase}"

  /**
    * Gets the entire history for a specific object. This specific function will always hit the
    * OSM servers and skip checking the cache. Although it will put the results in the cache. So
    * this function should be used sparingly
    *
    * @param id OSM ID of the object you are requesting
    * @param osmType The type of OSM object you are requesting
    * @return A list of VersionedObjects that is basically every version of the object
    */
  def getObjectHistory(id:Long, osmType:OSMType) : Future[List[T]] = {
    val p = Promise[List[T]]
    if (id < 0) {
      p success List.empty
    } else {
      ws.url(s"${this.getURLPrefix(osmType)}/$id/history").get() onComplete {
        case Success(res) =>
          // the result should be versions of the single object, which we can just create and dump in the cache
          p success (res.xml \ osmType.toString.toLowerCase).map(createVersionedObject).toList
        case Failure(f) => p failure f
      }
    }
    p.future
  }

  def get(ids:List[Long]) : Future[List[T]]

  /**
    * Gets the latest version for a series of objects
    *
    * @param ids
    * @param osmType
    * @return
    */
  protected def _get(ids:List[Long], osmType:OSMType) : Future[List[T]] = {
    val p = Promise[List[T]]
    if (ids.isEmpty) {
      p success List.empty
    } else {
      // get the list of cached objects, this will contain the versions of the object that actually
      // may not be the latest. If versions of object have changed, when it is attempted to conflate
      // the changes, it could fail, and then a further conflation attempt will be made.
      val allItems = ids.map(id => {
        this.getObjectFromCache(id) match {
          case Some(obj) => id -> obj.getLatest
          case None => id -> None
        }
      })
      val uncachedIds = allItems.filter(_._2.isEmpty).map(_._1)
      if (uncachedIds.isEmpty) {
        p success allItems.filter(_._2.isDefined).flatMap(_._2)
      } else {
        val objectURL = s"${this.getURLPrefix(osmType)}s?${osmType.toString.toLowerCase}s=${uncachedIds.mkString(",")}"
        ws.url(objectURL).get() onComplete {
          case Success(res) =>
            res.status match {
              case 200 =>
                val resultObjects = (res.xml \ osmType.toString.toLowerCase).map(createVersionedObject).toList
                val cachedObjects = allItems.filter(_._2.isDefined).flatMap(_._2)
                p success resultObjects ++ cachedObjects
              case _ => p failure new NotFoundException(s"${res.status} thrown for URL: $objectURL")
            }
          case Failure(f) => p failure f
        }
      }
    }
    p.future
  }

  protected def _createVersionedObject(elem:Node, id:Long, visible:Boolean, version:Int, changeset:Int, timestamp:DateTime, user:String, uid:Long, tags:Map[String, String]) : T

  private def createVersionedObject(elem:Node) : T = {
    val tags = (elem \ "tag").map(v => (v \ "@k").text -> (v \ "@v").text).toMap
    val id = (elem \ "@id").text.toLong
    val visible = (elem \ "@visible").text.toBoolean
    val version = (elem \ "@version").text.toInt
    val changeset = (elem \ "@changeset").text.toInt
    val timestamp = DateTime.parse((elem \ "@timestamp").text)
    val user = (elem \ "@user").text
    val uid = (elem \ "@uid").text.toLong
    this.addObjectToCache(this._createVersionedObject(elem, id, visible, version, changeset, timestamp, user, uid, tags))
  }
}

object ObjectService {
  val DEFAULT_CACHE_EXPIRY = 7200 // 2 hours
}
