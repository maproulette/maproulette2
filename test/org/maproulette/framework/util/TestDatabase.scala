/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.util

import play.api.db.Database
import play.api.db.evolutions.Evolutions
import play.api.inject.guice.GuiceApplicationBuilder

/**
  * A little helper class to create and drop test tables
  *
  * @author mcuthbert
  */
trait TestDatabase {
  implicit val application = GuiceApplicationBuilder()
    .configure(
      "db.default.url"                 -> "jdbc:postgresql://localhost:5432/mr_test",
      "db.default.username"            -> "osm",
      "db.default.password"            -> "osm",
      "db.default.logSql"              -> false,
      "maproulette.osm.consumerKey"    -> "test",
      "maproulette.osm.consumerSecret" -> "test",
      "maproulette.bootstrap"          -> true
    )
    .build()
  implicit val database = this.application.injector.instanceOf(classOf[Database])

  def applyEvolutions(): Unit = {
    Evolutions.cleanupEvolutions(database)
    Evolutions.applyEvolutions(database)
  }

  def cleanupEvolutions(): Unit = Evolutions.cleanupEvolutions(database)
}
