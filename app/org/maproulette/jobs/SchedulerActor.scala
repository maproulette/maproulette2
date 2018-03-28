// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.jobs

import javax.inject.{Inject, Singleton}

import akka.actor.{Actor, Props}
import play.api.{Application, Logger}
import play.api.db.Database
import anorm._
import anorm.JodaParameterMetaData._
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.jobs.SchedulerActor.RunJob
import org.maproulette.metrics.Metrics
import org.maproulette.models.Task
import org.maproulette.models.Task.STATUS_CREATED
import org.maproulette.models.dal.DALManager
import org.maproulette.session.User

/**
  * The main actor that handles all scheduled activities
  *
  * @author cuthbertm
  * @author davis_20
  */
@Singleton
class SchedulerActor @Inject() (config:Config,
                                application:Application,
                                db:Database,
                                dALManager: DALManager) extends Actor {
  val appConfig = application.configuration

  // cleanOldTasks configuration
  lazy val oldTasksStatusFilter = appConfig.getIntSeq(Config.KEY_SCHEDULER_CLEAN_TASKS_STATUS_FILTER).getOrElse(
    Seq(new Integer(STATUS_CREATED))
  )

  override def receive: Receive = {
    case RunJob("cleanLocks", action) => this.cleanLocks(action)
    case RunJob("runChallengeSchedules", action) => this.runChallengeSchedules(action)
    case RunJob("updateLocations", action) => this.updateLocations(action)
    case RunJob("cleanOldTasks", action) => this.cleanOldTasks(action)
    case RunJob("updateTaskLocations", action) => this.updateTaskLocations(action.toLong)
    case RunJob("cleanExpiredVirtualChallenges", action) => this.cleanExpiredVirtualChallenges(action)
    case RunJob("FindChangeSets", action) => this.findChangeSets(action)
    case RunJob("OSMChangesetMatcher", action) => this.matchChangeSets(action)
    case RunJob("cleanDeleted", action) => this.cleanDeleted(action)
  }

  /**
    * This job will remove all stale locks from the system. A stale lock is a lock that has maintained
    * the lock for over an hour. To enable, set:
    *    osm.scheduler.cleanLocks.interval=FiniteDuration
    */
  def cleanLocks(action:String) : Unit = {
    Logger.info(action)
    this.db.withTransaction { implicit c =>
      val locksDeleted = SQL"""DELETE FROM locked WHERE AGE(NOW(), locked_time) > '1 hour'""".executeUpdate()
      Logger.info(s"$locksDeleted were found and deleted.")
    }
  }

  /**
    * This job will update the challenges from remote geojson or overpass query based on the supplied
    * schedules in the challenge. To enable, set:
    *    osm.scheduler.runChallengeSchedules.interval=FiniteDuration
    */
  def runChallengeSchedules(action:String) : Unit = {
    Logger.info(action)
  }


  /**
    * This job will update the locations of all the challenges periodically. To enable, set:
    *    osm.scheduler.updateLocations.interval=FiniteDuration
    */
  def updateLocations(action:String) : Unit = {
    Logger.info(action)
    val currentTime = DateTime.now()
    db.withTransaction { implicit c =>
      val query = """DO $$
                      DECLARE
                        rec RECORD;
                      BEGIN
                        FOR rec IN SELECT id, modified, last_updated FROM challenges LOOP
                          UPDATE challenges SET location = (SELECT ST_Centroid(ST_Collect(ST_Makevalid(location)))
                                  FROM tasks
                                  WHERE parent_id = rec.id),
                                bounding = (SELECT ST_Envelope(ST_Buffer((ST_SetSRID(ST_Extent(location), 4326))::geography,2)::geometry)
                                  FROM tasks
                                  WHERE parent_id = rec.id)
                          WHERE id = rec.id AND (rec.modified > rec.last_updated OR rec.last_updated IS NULL);
                          UPDATE challenges SET last_updated = NOW()
                          WHERE id = rec.id AND (rec.modified > rec.last_updated OR rec.last_updated IS NULL);
                        END LOOP;
                      END$$;"""

      SQL(query).executeUpdate()
      c.commit()
      SQL("SELECT id FROM challenges WHERE last_updated > {currentTime}")
        .on('currentTime -> ParameterValue.toParameterValue(currentTime))
        .as(SqlParser.long("id").*)
        .foreach(id => {
          Logger.debug(s"Flushing challenge cache of challenge with id $id")
          this.dALManager.challenge.cacheManager.cache.remove(id)
        })
    }
    Logger.info("Completed updating challenge locations.")
  }

  /**
    * Makes sure that all the tasks for a particular challenge are updated
    *
    * @param challengeId The id of the challenge you want updated
    */
  def updateTaskLocations(challengeId:Long) : Unit = {
    Logger.info(s"Updating tasks for challenge $challengeId")
    this.dALManager.task.updateTaskLocations(challengeId)
  }

  /**
    * This job will delete old tasks, filtered by the statusFilter. To enable, set:
    *    osm.scheduler.cleanOldTasks.interval=FiniteDuration
    *    osm.scheduler.cleanOldTasks.olderThan=FiniteDuration
    */
  def cleanOldTasks(action:String) : Unit = {
    config.withFiniteDuration(Config.KEY_SCHEDULER_CLEAN_TASKS_OLDER_THAN) { duration =>
      Metrics.timer("Cleaning old challenge tasks") { () =>
        db.withTransaction { implicit c =>
          Logger.info(s"Cleaning old challenge tasks older than $duration with status [$oldTasksStatusFilter]...")
          val tasksDeleted =
            SQL("""DELETE FROM tasks t USING challenges c
                    WHERE t.parent_id = c.id AND c.updateTasks = true AND t.status IN ({statuses})
                     AND AGE(NOW(), c.modified) > {duration}::INTERVAL
                     AND AGE(NOW(), t.modified) > {duration}::INTERVAL""").on(
              'duration -> ParameterValue.toParameterValue(String.valueOf(duration)),
              'statuses -> ParameterValue.toParameterValue(oldTasksStatusFilter)
            ).executeUpdate()
          Logger.info(s"$tasksDeleted old challenge tasks were found and deleted.")
          // Clear the task cache if any were deleted
          if (tasksDeleted > 0) {
            this.dALManager.task.cacheManager.clearCaches
          }
        }
      }
    }
  }

  /**
    * This job will delete expired Virtual Challenges. To enable, set:
    *    osm.scheduler.cleanExpiredVCs.interval=FiniteDuration
    */
  def cleanExpiredVirtualChallenges(str: String) : Unit = {
    db.withConnection { implicit c =>
      val numberOfDeleted = SQL"""DELETE FROM virtual_challenges WHERE expired < NOW()""".executeUpdate()
      Logger.info(s"$numberOfDeleted Virtual Challenges expired and removed from database")
      // Clear the task cache if any were deleted
      if (numberOfDeleted > 0) {
        this.dALManager.virtualChallenge.cacheManager.clearCaches
      }
    }
  }

  /**
    * Run through all the tasks and match OSM Changesets to fixed tasks. This will run through tasks
    * 5000 at a time, and limit the tasks returned to only tasks that have actually had their status
    * set to FIXED and changeset value not set to -2. If the value is -2 then it assumes that we have
    * already tried to match the changeset and couldn't find any viable option for it.
    *
    * @param str
    */
  def matchChangeSets(str:String) : Unit = {
    if (config.osmMatcherEnabled) {
      db.withConnection { implicit c =>
        val query =
          s"""
             |SELECT ${dALManager.task.retrieveColumns} FROM tasks
             |WHERE status = 1 AND changeset_id = -1
             |LIMIT ${config.osmMatcherBatchSize}
         """.stripMargin
        SQL(query).as(dALManager.task.parser.*).foreach(t => {
          dALManager.task.matchToOSMChangeSet(t, User.superUser)
        })
      }
    }
  }

  /**
    * Task that manually matches the OSM changesets to tasks
    *
    * @param str
    */
  def findChangeSets(str: String) : Unit = {
    if (config.osmMatcherManualOnly) {
      val values = str.split("=")
      if (values.size == 2) {
        implicit val id = values(1).toLong
        values(0) match {
          case "p" =>
            dALManager.project.listChildren(-1).foreach(c => {
              dALManager.challenge.listChildren(-1)(c.id).filter(_.status == Task.STATUS_FIXED).foreach(t =>
                dALManager.task.matchToOSMChangeSet(t, User.superUser, false)
              )
            })
          case "c" =>
            dALManager.challenge.listChildren(-1).foreach(t => {
              dALManager.task.matchToOSMChangeSet(t, User.superUser, false)
            })
          case "t" =>
            dALManager.task.retrieveById match {
              case Some(t) => dALManager.task.matchToOSMChangeSet(t, User.superUser, false)
              case None =>
            }
          case _ => // Do nothing because there is nothing to do
        }
      }
    }
  }

  def cleanDeleted(action:String) : Unit = {
    Logger.info(action)
    db.withConnection { implicit c =>
      val deletedProjects = SQL"DELETE FROM projects WHERE deleted = true RETURNING id".as(SqlParser.int("id").*)
      if (deletedProjects.nonEmpty) {
        Logger.debug(s"Finalized deletion of projects with id [${deletedProjects.mkString(",")}]")
      }
      val deletedChallenges = SQL"DELETE FROM challenges WHERE deleted = true RETURNING id".as(SqlParser.int("id").*)
      if (deletedChallenges.nonEmpty) {
        Logger.debug(s"Finalized deletion of challenges with id [${deletedChallenges.mkString(",")}]")
      }
    }
  }
}

object SchedulerActor {
  def props = Props[SchedulerActor]

  case class RunJob(name:String, action:String="")
}
