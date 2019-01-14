package org.maproulette.cache

/**
  * @author mcuthbert
  */
trait CacheObject[Key] {
  def name:String
  def id:Key
}
