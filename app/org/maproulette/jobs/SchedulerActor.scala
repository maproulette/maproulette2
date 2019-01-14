// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.jobs

import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.format.DateTimeFormatter;
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
import org.maproulette.services.{KeepRight, KeepRightBox, KeepRightError, KeepRightService}
import org.maproulette.session.User

import scala.util.{Failure, Success}

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
                                dALManager: DALManager,
                                keepRightService: KeepRightService) extends Actor {
  val appConfig = application.configuration
  import scala.concurrent.ExecutionContext.Implicits.global

  // cleanOldTasks configuration
  lazy val oldTasksStatusFilter = appConfig.getOptional[Seq[Int]](Config.KEY_SCHEDULER_CLEAN_TASKS_STATUS_FILTER).getOrElse(
    Seq[Int](new Integer(STATUS_CREATED))
  )

  override def receive: Receive = {
    case RunJob("rebuildChallengesLeaderboard", action) => this.rebuildChallengesLeaderboard(action)
    case RunJob("cleanLocks", action) => this.cleanLocks(action)
    case RunJob("runChallengeSchedules", action) => this.runChallengeSchedules(action)
    case RunJob("updateLocations", action) => this.updateLocations(action)
    case RunJob("cleanOldTasks", action) => this.cleanOldTasks(action)
    case RunJob("updateTaskLocations", action) => this.updateTaskLocations(action.toLong)
    case RunJob("cleanExpiredVirtualChallenges", action) => this.cleanExpiredVirtualChallenges(action)
    case RunJob("FindChangeSets", action) => this.findChangeSets(action)
    case RunJob("OSMChangesetMatcher", action) => this.matchChangeSets(action)
    case RunJob("cleanDeleted", action) => this.cleanDeleted(action)
    case RunJob("KeepRightUpdate", action) => this.keepRightUpdate(action)
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
        .on('currentTime -> ToParameterValue.apply[DateTime].apply(currentTime))
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
              'duration -> ToParameterValue.apply[String].apply(String.valueOf(duration)),
              'statuses -> ToParameterValue.apply[Seq[Int]].apply(oldTasksStatusFilter)
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
              dALManager.challenge.listChildren(-1)(c.id).filter(_.status.contains(Task.STATUS_FIXED)).foreach(t =>
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

  def keepRightUpdate(action:String) : Unit = {
    Logger.info(action)
    val slidingValue = this.config.config.getOptional[Int](KeepRight.KEY_SLIDING).getOrElse(KeepRight.DEFAULT_SLIDING)
    val slidingErrors = keepRightService.errorList.sliding(slidingValue, slidingValue).toList

    val integrationList:List[(List[KeepRightError], KeepRightBox)] =
      if (config.config.getOptional[Boolean](KeepRight.KEY_ENABLED).getOrElse(false)) {
        if (keepRightService.boundingBoxes.nonEmpty && keepRightService.errorList.nonEmpty) {
          slidingErrors.flatMap(error =>
            keepRightService.boundingBoxes map { bounding =>
              (error, bounding)
            }
          )
        } else {
          List.empty
        }
      } else {
        List.empty
      }
    integrationList.headOption match {
      case Some(h) => this._integrateKeepRight(h, integrationList.tail)
      case None => //just do nothing
    }
  }

  /**
    * We essentially create this recursive function, so that we don't take down the KeepRight servers
    * by bombarding it with tons of API requests.
    *
    * @param head The head of the list, which is a tuple containing a KeepRightError and a KeepRightBox
    * @param tail The tail list of box objects
    */
  private def _integrateKeepRight(head:(List[KeepRightError], KeepRightBox),
                                  tail:List[(List[KeepRightError], KeepRightBox)]) : Unit = {
    keepRightService.integrate(head._1.map(_.id), head._2) onComplete {
      case Success(x) =>
        if (!x) {
          Logger.warn(s"KeepRight challenge failed, but continuing to next one")
        }
        tail.headOption match {
          case Some(head) => this._integrateKeepRight(head, tail.tail)
          case None => // just do nothing because we are finished
        }
      case Failure(f) =>
        // something went wrong, we should bail out immediately
        Logger.warn(s"The KeepRight challenge creation failed. ${f.getMessage}")
    }
  }

  /**
   * Rebuilds the user_leaderboard table.
   *
   * @param action - action string
   */
  def rebuildChallengesLeaderboard(action:String) : Unit = {
    val start = System.currentTimeMillis
    Logger.info(action)

    db.withConnection { implicit c =>
      // Clear TABLEs
      SQL("DELETE FROM user_leaderboard").executeUpdate()
      SQL("DELETE FROM user_top_challenges").executeUpdate()

      // Past Month
      SQL(this.rebuildChallengesLeaderboardSQL(1)).executeUpdate()
      SQL(this.rebuildTopChallengesSQL(1)).executeUpdate()

      // Past 3 Months
      SQL(this.rebuildChallengesLeaderboardSQL(3)).executeUpdate()
      SQL(this.rebuildTopChallengesSQL(3)).executeUpdate()

      // Past 6 Months
      SQL(this.rebuildChallengesLeaderboardSQL(6)).executeUpdate()
      SQL(this.rebuildTopChallengesSQL(6)).executeUpdate()

      // Past Year
      SQL(this.rebuildChallengesLeaderboardSQL(12)).executeUpdate()
      SQL(this.rebuildTopChallengesSQL(12)).executeUpdate()

      Logger.info(s"Rebuilt Challenges Leaderboard succesfully.")
      val totalTime = System.currentTimeMillis - start
      Logger.debug("Time to rebuild leaderboard: %1d ms".format(totalTime))

    }
  }

  /**
   * Returns the SQL to rebuild the Challenges Leaderboard table with the
   * given dates
   *
   * @param start_date
   * @param end_date
   */
  private def rebuildChallengesLeaderboardSQL(monthDuration:Integer) : String = {
    val today = LocalDate.now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val startMonth = LocalDate.now.minus(Period.ofMonths(monthDuration)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    s"""INSERT INTO user_leaderboard
        (month_duration, user_id, user_name, user_avatar_url, user_ranking, user_score)
        SELECT $monthDuration, users.id, users.name, users.avatar_url,
                ROW_NUMBER() OVER( ORDER BY ${this.scoreSumSQL()} DESC, sa.osm_user_id ASC),
                ${this.scoreSumSQL()} AS score
                FROM status_actions sa, users
                WHERE sa.created::DATE BETWEEN '$startMonth' AND '$today' AND
                      sa.old_status <> sa.status AND
                      users.osm_id = sa.osm_user_id AND
                      users.leaderboard_opt_out = FALSE
                GROUP BY sa.osm_user_id, users.id
                ORDER BY score DESC, sa.osm_user_id ASC"""
  }

  /**
   * Returns the SQL to rebuild the Top Challenges table with the
   * given dates
   *
   * @param start_date
   * @param end_date
   */
  private def rebuildTopChallengesSQL(monthDuration:Integer) : String = {
    val today = LocalDate.now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val startMonth = LocalDate.now.minus(Period.ofMonths(monthDuration)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    s"""INSERT INTO user_top_challenges
        (month_duration, user_id, challenge_id, challenge_name, activity)
        SELECT $monthDuration, u.id, sa.challenge_id, c.name, count(sa.challenge_id) as activity
          FROM status_actions sa, challenges c, projects p, users u
          WHERE sa.created::DATE BETWEEN '$startMonth' AND '$today' AND
            sa.osm_user_id = u.osm_id AND sa.challenge_id = c.id AND
            p.id = sa.project_id AND c.enabled = TRUE and p.enabled = TRUE
          GROUP BY sa.challenge_id, c.name, u.id"""
  }

  /**
   * Returns the SQL to sum a user's status actions for ranking purposes
   **/
  private def scoreSumSQL(statusActionsTableName:String="sa") : String = {
    s"""SUM(CASE ${statusActionsTableName}.status
             WHEN ${Task.STATUS_FIXED} THEN ${config.taskScoreFixed}
             WHEN ${Task.STATUS_FALSE_POSITIVE} THEN ${config.taskScoreFalsePositive}
             WHEN ${Task.STATUS_ALREADY_FIXED} THEN ${config.taskScoreAlreadyFixed}
             WHEN ${Task.STATUS_TOO_HARD} THEN ${config.taskScoreTooHard}
             WHEN ${Task.STATUS_SKIPPED} THEN ${config.taskScoreSkipped}
             ELSE 0
           END)"""
  }

}

object SchedulerActor {
  def props = Props[SchedulerActor]

  case class RunJob(name:String, action:String="")
}
