/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */
package org.maproulette.jobs

import anorm._
import javax.inject.{Inject, Singleton}
import org.maproulette.Config
import org.slf4j.LoggerFactory
import play.api.db.Database
import play.api.inject.ApplicationLifecycle

import scala.concurrent.Future

/**
  * @author mcuthbert
  */
@Singleton
class Bootstrap @Inject() (appLifeCycle: ApplicationLifecycle, db: Database, config: Config) {

  private val logger = LoggerFactory.getLogger(this.getClass)

  def start(): Unit = {
    if (!config.isBootstrapMode) {
      // for startup we make sure that all the super users are set correctly
      db.withConnection { implicit c =>
        SQL"""DELETE FROM user_groups
              WHERE group_id = -999 AND NOT osm_user_id = -999
          """.executeUpdate()
        // make sure all the super user id's are in the super user group
        config.superAccounts.headOption match {
          case Some("*") =>
            logger.info(
              "WARNING: Configuration is setting all users to super users. Make sure this is what you want."
            )
            SQL"""INSERT INTO user_groups (group_id, osm_user_id) (SELECT -999 AS group_id, osm_id FROM users WHERE NOT osm_id = -999)"""
              .executeUpdate()
          case Some("") =>
            logger.info(
              "WARNING: Configuration has NO super users. Make sure this is what you want."
            )
          case _ =>
            val inserts = config.superAccounts.map(s => s"(-999, $s)").mkString(",")
            SQL"""INSERT INTO user_groups (group_id, osm_user_id) VALUES #$inserts"""
              .executeUpdate()
        }
      }
    }
  }

  appLifeCycle.addStopHook { () =>
    Future.successful(())
  }

  start()
}
