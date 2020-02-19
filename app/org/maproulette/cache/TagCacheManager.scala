// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.cache

import java.util.concurrent.locks.{ReadWriteLock, ReentrantReadWriteLock}

import anorm._
import javax.inject.{Inject, Provider, Singleton}
import org.maproulette.Config
import org.maproulette.models.Tag
import org.maproulette.models.dal.TagDAL
import play.api.db.Database

/**
  * This is not currently a real cache manager, it will just store elements that have been loaded into memory.
  * Using this too much will most likely end up in OutOfMemory exceptions, so this needs to be readdressed
  * prior to a live version of this service. The CacheStorage is the area that you would be required
  * to be modified.
  *
  * @author cuthbertm
  */
@Singleton
class TagCacheManager @Inject() (tagDAL: Provider[TagDAL], db: Database, config: Config)
    extends CacheManager[Long, Tag](config, Config.CACHE_ID_TAGS) {

  private val loadingLock: ReadWriteLock = new ReentrantReadWriteLock()

  def reloadTags: Unit = {
    this.loadingLock.writeLock().lock()
    try {
      db.withConnection { implicit c =>
        this.cache.clear()
        SQL"""SELECT * FROM tags""".as(tagDAL.get.parser.*).foreach(tag => cache.addObject(tag))
      }
    } finally {
      this.loadingLock.writeLock().unlock()
    }
  }
}
