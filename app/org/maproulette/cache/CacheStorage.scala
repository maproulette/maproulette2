// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.cache

/**
  * This is a very basic Cache Storage class that will store all items in memory. Ultimately this
  * class should be extended to use the Play Cache API
  *
  * cacheLimit - The number of entries allowed in the cache until it starts kicking out the oldest entries
  * cacheExpiry - The number of seconds allowed until a cache item is expired out the cache lazily
  *
  * @author cuthbertm
  */
class CacheStorage[Key, Value<:CacheObject[Key]] (cacheLimit:Int=CacheManager.DEFAULT_CACHE_LIMIT, cacheExpiry:Int=CacheManager.DEFAULT_CACHE_EXPIRY)
  extends BasicCache[Key, Value](cacheLimit, cacheExpiry) {

  /**
    * Finds an object from the cache based on the name instead of the id
    *
    * @param name The name of the object you wish to find
    * @return The object from the cache, None if not found
    */
  def find(name:String) : Option[Value] = synchronized {
    this.cache.find(element => element._2.value.name.equalsIgnoreCase(name)) match {
      case Some(value) =>
        if (isExpired(value._2)) {
          None
        } else {
          Some(value._2.value)
        }
      case None => None
    }
  }

  /**
    * Adds an object to the cache, if cache limit has been reached, then will remove the oldest
    * accessed item in the cache
    *
    * @param obj The object to add to the cache
    * @param localExpiry You can add a custom expiry to a specific element in seconds
    * @return The object put in the cache, or None if it could not be placed in the cache
    */
  def addObject(obj:Value, localExpiry:Option[Int]=None) : Option[Value] = synchronized {
    this.add(obj.id, obj, localExpiry)
  }

  /**
    * Remove an object from the cache based on the name
    *
    * @param name The name of the object to be removed
    * @return The object removed from the cache, or None if it could not be removed from the cache,
    *         or was not originally in the cache
    */
  def remove(name:String) : Option[Value] = synchronized {
    this.find(name) match {
      case Some(value) => this.remove(value.id)
      case None => None
    }
  }

  private def isExpiredByKey(key:Key) : Boolean = synchronized {
    this.cache.get(key) match {
      case Some(value) => isExpired(value)
      case None => true
    }
  }
}
