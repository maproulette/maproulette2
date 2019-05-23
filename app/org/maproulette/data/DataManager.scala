// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.data

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.models.Task
import org.maproulette.models.utils.{AND, DALHelper, WHERE}
import org.maproulette.utils.BoundingBoxFinder
import play.api.Application
import play.api.db.Database

case class ActionSummary(total: Double,
                         available: Double,
                         fixed: Double,
                         falsePositive: Double,
                         skipped: Double,
                         deleted: Double,
                         alreadyFixed: Double,
                         tooHard: Double,
                         answered: Double) {
  def percentComplete: Double = (((trueAvailable / total) * 100) - 100) * -1

  // available in the database means it is created state, available in the UI, means that it is in state
  // AVAILABLE, SKIPPED or TOO HARD
  def trueAvailable: Double = available + skipped + deleted

  def percentage(value: Double): Double = (value / total) * 100
}

/**
  * Handles the summary data for the users
  *
  * @param distinctAllUsers              This is all the distinct users regardless of the date range
  * @param distinctTotalUsers            All the distinct users within the supplied date range
  * @param avgUsersPerChallenge          Average number of users per challenge within the date range
  * @param activeUsers                   Active users (2 or more edits in last 2 days)
  * @param avgActionsPerUser             Average number of actions taken by a user
  * @param avgActionsPerChallengePerUser Average number of actions taken by user per challenge
  */
case class UserSummary(distinctAllUsers: Int,
                       distinctTotalUsers: Int,
                       avgUsersPerChallenge: Double,
                       activeUsers: Double,
                       avgActionsPerUser: ActionSummary,
                       avgActionsPerChallengePerUser: ActionSummary)

case class UserSurveySummary(distinctTotalUsers: Int,
                             avgUsersPerChallenge: Double,
                             activeUsers: Double,
                             answerPerUser: Double,
                             answersPerChallenge: Double)

case class ChallengeSummary(id: Long, name: String, actions: ActionSummary)

case class SurveySummary(id: Long, name: String, count: Int)

case class ChallengeActivity(date: DateTime, status: Int, statusName: String, count: Int)

case class RawActivity(date: DateTime, osmUserId: Long, osmUsername: String, projectId: Long,
                       projectName: String, challengeId: Long, challengeName: String,
                       taskId: Long, oldStatus: Int, status: Int)

case class LeaderboardChallenge(id: Long, name: String, activity: Int)

case class LeaderboardUser(userId: Long, name: String, avatarURL: String,
                           score: Int, rank: Int, topChallenges: List[LeaderboardChallenge])

/**
  * @author cuthbertm
  */
@Singleton
class DataManager @Inject()(config: Config, db: Database, boundingBoxFinder: BoundingBoxFinder)(implicit application: Application) extends DALHelper {
  def getUserSurveySummary(projectList: Option[List[Long]] = None, surveyId: Option[Long] = None,
                           start: Option[DateTime] = None, end: Option[DateTime] = None, priority: Option[Int]): UserSurveySummary = {
    this.db.withConnection { implicit c =>
      val surveyProjectFilter = surveyId match {
        case Some(id) => s"AND survey_id = $id"
        case None => getLongListFilter(projectList, "project_id")
      }

      val perUser: Double =
        SQL"""SELECT AVG(answered) AS answered FROM (
                            SELECT osm_user_id, COUNT(DISTINCT answer_id) AS answered
                            FROM survey_answers sa
                            #${this.getEnabledPriorityClause(surveyId.isEmpty, true, start, end, priority)}
                            #$surveyProjectFilter
                            GROUP BY osm_user_id
                          ) AS t""".as(get[Option[Double]]("answered").single).getOrElse(0)
      val perSurvey: Double =
        SQL"""SELECT AVG(answered) AS answered FROM (
                              SELECT osm_user_id, survey_id, COUNT(DISTINCT answer_id) AS answered
                              FROM survey_answers sa
                              #${this.getEnabledPriorityClause(surveyId.isEmpty, true, start, end, priority)}
                              #$surveyProjectFilter
                              GROUP BY osm_user_id, survey_id
                            ) AS t""".as(get[Option[Double]]("answered").single).getOrElse(0)

      UserSurveySummary(this.getDistinctUsers(surveyProjectFilter, true, surveyId.isEmpty, start, end, priority),
        this.getDistinctUsersPerChallenge(surveyProjectFilter, true, surveyId.isEmpty, start, end, priority),
        this.getActiveUsers(surveyProjectFilter, true, surveyId.isEmpty, start, end, priority),
        perUser,
        perSurvey
      )
    }
  }

  private def getDistinctUsers(projectFilter: String, survey: Boolean = false, onlyEnabled: Boolean = true,
                               start: Option[DateTime] = None, end: Option[DateTime] = None, priority: Option[Int])(implicit c: Connection): Int = {
    SQL"""SELECT COUNT(DISTINCT osm_user_id) AS count
             FROM #${
      if (survey) {
        "survey_answers sa"
      } else {
        "status_actions sa"
      }
    }
             #${this.getEnabledPriorityClause(onlyEnabled, survey, start, end, priority)}
             #$projectFilter""".as(get[Option[Int]]("count").single).getOrElse(0)
  }

  private def getEnabledPriorityClause(onlyEnabled: Boolean = true, isSurvey: Boolean = true,
                                       start: Option[DateTime] = None, end: Option[DateTime] = None,
                                       priority: Option[Int] = None, ignoreDates: Boolean = false): String = {
    val priorityClauses = priority match {
      case Some(p) => ("INNER JOIN tasks t ON t.id = sa.task_id", s"AND t.priority = $p")
      case None => ("", "")
    }
    if (onlyEnabled) {
      s"""|INNER JOIN challenges c ON c.id = sa.${
        if (isSurvey) {
          "survey_id"
        } else {
          "challenge_id"
        }
      }
          |${priorityClauses._1}
          |INNER JOIN projects p ON p.id = c.parent_id
          |WHERE c.enabled = true and p.enabled = true
          |${priorityClauses._2}
          |${
        if (!ignoreDates) {
          getDateClause("sa.created", start, end)(Some(AND()))
        } else {
          ""
        }
      }
       """.stripMargin
    } else if (!ignoreDates) {
      s"""
         |${priorityClauses._1}
         |${getDateClause("sa.created", start, end)(Some(WHERE()))}
         |${priorityClauses._2}
       """.stripMargin
    } else {
      s"""
         |${priorityClauses._1}
         |WHERE 1=1 ${priorityClauses._2}
       """.stripMargin
    }
  }

  private def getDistinctUsersPerChallenge(projectFilter: String, survey: Boolean = false, onlyEnabled: Boolean = true,
                                           start: Option[DateTime] = None, end: Option[DateTime] = None, priority: Option[Int])(implicit c: Connection): Double = {
    SQL"""SELECT AVG(count) AS count FROM (
            SELECT #${
      if (survey) {
        "survey_id"
      } else {
        "challenge_id"
      }
    }, COUNT(DISTINCT osm_user_id) AS count
            FROM #${
      if (survey) {
        "survey_answers sa"
      } else {
        "status_actions sa"
      }
    }
            #${this.getEnabledPriorityClause(onlyEnabled, survey, start, end, priority)} #$projectFilter
            GROUP BY #${
      if (survey) {
        "survey_id"
      } else {
        "challenge_id"
      }
    }
          ) as t""".as(get[Option[Double]]("count").single).getOrElse(0D)
  }

  private def getActiveUsers(projectFilter: String, survey: Boolean = false, onlyEnabled: Boolean = true,
                             start: Option[DateTime] = None, end: Option[DateTime] = None, priority: Option[Int])(implicit c: Connection): Int = {
    SQL"""SELECT COUNT(DISTINCT osm_user_id) AS count
          FROM #${
      if (survey) {
        "survey_answers sa"
      } else {
        "status_actions sa"
      }
    }
          #${this.getEnabledPriorityClause(onlyEnabled, survey, start, end, priority)}  #$projectFilter
          AND sa.created::date BETWEEN current_date - INTERVAL '2 days' AND current_date"""
      .as(get[Option[Int]]("count").single).getOrElse(0)
  }

  private def buildProjectSearch(projectList: Option[List[Long]] = None, projectColumn: String, challengeColumn: String): String = {
    projectList match {
      case Some(idList) if idList.nonEmpty =>
        s"""AND ($projectColumn IN (${idList.mkString(",")})
                 OR 1 IN (SELECT 1 FROM unnest(ARRAY[${idList.mkString(",")}]) AS pIds
                     WHERE pIds IN (SELECT vp.project_id FROM virtual_project_challenges vp
                                    WHERE vp.challenge_id = ${challengeColumn})))"""
      case _ => ""
    }
  }

  private def findRelevantChallenges(projectList: Option[List[Long]]): Option[List[Long]] = {
    this.db.withConnection { implicit c =>
      // Let's determine all the challenges that are in these projects
      // to make our query faster.
      implicit val conjunction = Some(WHERE())
      val projectChallengeQuery =
        s"""SELECT id FROM challenges
         ${getLongListFilter(projectList, "parent_id")} OR id IN
          (SELECT challenge_id FROM virtual_project_challenges vp
           ${getLongListFilter(projectList, "vp.project_id")})
         """
      Some(SQL(projectChallengeQuery).as(long("id").*))
    }
  }

  def getUserChallengeSummary(projectList: Option[List[Long]] = None, challengeId: Option[Long] = None,
                              start: Option[DateTime] = None, end: Option[DateTime] = None, priority: Option[Int]): UserSummary = {
    this.db.withConnection { implicit c =>
      val challengeProjectFilter = challengeId match {
        case Some(id) => s"AND sa.challenge_id = $id"
        case None => buildProjectSearch(projectList, "sa.project_id", "sa.challenge_id")
      }
      val actionParser = for {
        available <- get[Option[Double]]("available")
        fixed <- get[Option[Double]]("fixed")
        falsePositive <- get[Option[Double]]("false_positive")
        skipped <- get[Option[Double]]("skipped")
        deleted <- get[Option[Double]]("deleted")
        alreadyFixed <- get[Option[Double]]("already_fixed")
        tooHard <- get[Option[Double]]("too_hard")
        answered <- get[Option[Double]]("answered")
      } yield ActionSummary(0, available.getOrElse(0), fixed.getOrElse(0), falsePositive.getOrElse(0),
        skipped.getOrElse(0), deleted.getOrElse(0), alreadyFixed.getOrElse(0),
        tooHard.getOrElse(0), answered.getOrElse(0))

      val perUser =
        SQL"""SELECT AVG(available) AS available, AVG(fixed) AS fixed, AVG(false_positive) AS false_positive,
                                  AVG(skipped) AS skipped, AVG(deleted) AS deleted, AVG(already_fixed) AS already_fixed,
                                  AVG(too_hard) AS too_hard, AVG(answered) AS answered
                              FROM (
                                SELECT sa.osm_user_id,
                                    SUM(CASE sa.status WHEN 0 THEN 1 ELSE 0 END) AS available,
                                    SUM(CASE sa.status WHEN 1 THEN 1 ELSE 0 END) AS fixed,
                                    SUM(CASE sa.status WHEN 2 THEN 1 ELSE 0 END) AS false_positive,
                                    SUM(CASE sa.status WHEN 3 THEN 1 ELSE 0 END) AS skipped,
                                    SUM(CASE sa.status WHEN 4 THEN 1 ELSE 0 END) AS deleted,
                                    SUM(CASE sa.status WHEN 5 THEN 1 ELSE 0 END) AS already_fixed,
                                    SUM(CASE sa.status WHEN 6 THEN 1 ELSE 0 END) AS too_hard,
                                    SUM(CASE sa.status WHEN 7 THEN 1 ELSE 0 END) AS answered
                                FROM status_actions sa
                                #${this.getEnabledPriorityClause(challengeId.isEmpty, false, start, end, priority)}
                                #$challengeProjectFilter
                                GROUP BY sa.osm_user_id
                              ) AS t""".as(actionParser.*).head
      val perChallenge =
        SQL"""SELECT AVG(available) AS available, AVG(fixed) AS fixed, AVG(false_positive) AS false_positive,
                                        AVG(skipped) AS skipped, AVG(deleted) AS deleted, AVG(already_fixed) AS already_fixed,
                                        AVG(too_hard) AS too_hard, AVG(answered) AS answered
                                  FROM (
                                    SELECT osm_user_id, challenge_id,
                                        SUM(CASE sa.status WHEN 0 THEN 1 ELSE 0 END) AS available,
                                        SUM(CASE sa.status WHEN 1 THEN 1 ELSE 0 END) AS fixed,
                                        SUM(CASE sa.status WHEN 2 THEN 1 ELSE 0 END) AS false_positive,
                                        SUM(CASE sa.status WHEN 3 THEN 1 ELSE 0 END) AS skipped,
                                        SUM(CASE sa.status WHEN 4 THEN 1 ELSE 0 END) AS deleted,
                                        SUM(CASE sa.status WHEN 5 THEN 1 ELSE 0 END) AS already_fixed,
                                        SUM(CASE sa.status WHEN 6 THEN 1 ELSE 0 END) AS too_hard,
                                        SUM(CASE sa.status WHEN 7 THEN 1 ELSE 0 END) AS answered
                                    FROM status_actions sa
                                    #${this.getEnabledPriorityClause(challengeId.isEmpty, false, start, end, priority)}
                                    #$challengeProjectFilter
                                    GROUP BY osm_user_id, challenge_id
                                  ) AS t""".as(actionParser.*).head
      val allUsers =
        SQL"""SELECT count(DISTINCT osm_user_id) FROM status_actions sa
              #${this.getEnabledPriorityClause(challengeId.isEmpty, false, None, None, priority, true)}
              #$challengeProjectFilter
           """.as(int(1).single)
      UserSummary(allUsers,
        this.getDistinctUsers(challengeProjectFilter, false, challengeId.isEmpty, start, end, priority),
        this.getDistinctUsersPerChallenge(challengeProjectFilter, false, challengeId.isEmpty, start, end, priority),
        this.getActiveUsers(challengeProjectFilter, false, challengeId.isEmpty, start, end, priority),
        perUser,
        perChallenge)
    }
  }

  /**
    * Gets the summarized survey data
    *
    * @param surveyId The id for the survey
    * @param priority The optional priority value
    * @return a list of SurveySummary object
    */
  def getSurveySummary(surveyId: Long, priority: Option[Int] = None): List[SurveySummary] = {
    this.db.withConnection { implicit c =>
      val parser = for {
        answer_id <- int("answer_id")
        answer <- str("answer")
        count <- int("count")
      } yield SurveySummary(answer_id, answer, count)
      val priorityFilter = priority match {
        case Some(p) => s"AND t.priority = $p"
        case None => ""
      }
      SQL"""(SELECT answer_id, answer, COUNT(answer_id)
            FROM survey_answers sa
            INNER JOIN answers a ON a.id = sa.answer_id
            INNER JOIN tasks t ON t.id = sa.task_id
            WHERE sa.survey_id = $surveyId
            #$priorityFilter
            GROUP BY answer_id, answer)
            UNION
            (
              SELECT -3, 'total', COUNT(*) FROM tasks t WHERE parent_id = $surveyId #$priorityFilter
            )
        """.as(parser.*)
    }
  }

  /**
    * Gets the summarized challenge activity
    *
    * @param projectList The projects to filter by default None, will assume all projects
    * @param challengeId The challenge to filter by default None, if set will ignore the projects parameter
    * @return
    */
  def getChallengeSummary(projectList: Option[List[Long]] = None, challengeId: Option[Long] = None,
                          limit: Int = (-1), offset: Int = 0, orderColumn: Option[String] = None, orderDirection: String = "ASC",
                          searchString: String = "", priority: Option[Int] = None, onlyEnabled: Boolean = false): List[ChallengeSummary] = {
    this.db.withConnection { implicit c =>
      val parser = for {
        id <- int("tasks.parent_id")
        name <- str("challenges.name")
        total <- int("total")
        available <- int("available")
        fixed <- int("fixed")
        falsePositive <- int("false_positive")
        skipped <- int("skipped")
        deleted <- int("deleted")
        alreadyFixed <- int("already_fixed")
        tooHard <- int("too_hard")
        answered <- int("answered")
      } yield ChallengeSummary(id, name, ActionSummary(total, available, fixed, falsePositive,
        skipped, deleted, alreadyFixed, tooHard, answered))
      val challengeFilter = challengeId match {
        case Some(id) if id != -1 => s"AND t.parent_id = $id"
        case _ => buildProjectSearch(projectList, "c.parent_id", "c.id")
      }
      val priorityFilter = priority match {
        case Some(p) => s"AND t.priority = $p"
        case None => ""
      }
      // The percentage columns are a bit of a hack simply so that we can order by the percentages.
      // It won't decrease performance as this is simple basic math calculations, but it certainly
      // isn't pretty
      val query =
      s"""SELECT *,
                        (((CAST(available AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100)-100)*1 AS complete_percentage,
                        (CAST(available AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS available_perc,
                        (CAST(fixed AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS fixed_perc,
                        (CAST(false_positive AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS false_positive_perc,
                        (CAST(skipped AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS skipped_perc,
                        (CAST(already_fixed AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS already_fixed_perc,
                        (CAST(too_hard AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS too_hard_perc,
                        (CAST(answered AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS answered_perc
                      FROM (
                      SELECT t.parent_id, c.name,
                                SUM(CASE WHEN t.status != 4 THEN 1 ELSE 0 END) as total,
                                SUM(CASE t.status WHEN 0 THEN 1 ELSE 0 END) as available,
                                SUM(CASE t.status WHEN 1 THEN 1 ELSE 0 END) as fixed,
                                SUM(CASE t.status WHEN 2 THEN 1 ELSE 0 END) as false_positive,
                                SUM(CASE t.status WHEN 3 THEN 1 ELSE 0 END) as skipped,
                                SUM(CASE t.status WHEN 4 THEN 1 ELSE 0 END) as deleted,
                                SUM(CASE t.status WHEN 5 THEN 1 ELSE 0 END) as already_fixed,
                                SUM(CASE t.status WHEN 6 THEN 1 ELSE 0 END) AS too_hard,
                                SUM(CASE t.status WHEN 7 THEN 1 ELSE 0 END) AS answered
                              FROM tasks t
                              INNER JOIN challenges c ON c.id = t.parent_id
                              INNER JOIN projects p ON p.id = c.parent_id
                              WHERE ${
        if (onlyEnabled) {
          "c.enabled = true AND p.enabled = true AND"
        } else {
          ""
        }
      }
                              challenge_type = ${Actions.ITEM_TYPE_CHALLENGE} $challengeFilter $priorityFilter
                              ${searchField("c.name")}
                              GROUP BY t.parent_id, c.name
                    ) AS t
                    ${this.order(orderColumn, orderDirection)}
                    LIMIT ${this.sqlLimit(limit)} OFFSET {offset}
        """

      SQL(query).on('ss -> this.search(searchString), 'offset -> offset).as(parser.*)
    }
  }

  /**
    * Should be used in conjunction with challenge summary to retrieve the total number of challenges that
    * are set for the particular summary query
    *
    * @param projectList  The projects that are being used to filter the results, optional
    * @param challengeId  The challenge used to filter the results, optional
    * @param searchString The search string that was applied to the query
    * @return A integer value which is the total challenges included in the results
    *
    * @deprecated("This method does not support virtual projects.", "05-23-2019")
    */
  def getTotalSummaryCount(projectList: Option[List[Long]] = None, challengeId: Option[Long] = None, searchString: String = ""): Int = {
    this.db.withConnection { implicit c =>
      val challengeFilter = challengeId match {
        case Some(id) if id != -1 => s"AND id = $id"
        case _ => getLongListFilter(projectList, "c.parent_id")
      }
      val query =
        s"""SELECT COUNT(*) AS total FROM challenges c
                      INNER JOIN projects p ON p.id = c.parent_id
                      WHERE ${
          if (challengeId.isEmpty) {
            "c.enabled = true AND p.enabled = true AND"
          } else {
            ""
          }
        }
                      challenge_type = ${Actions.ITEM_TYPE_CHALLENGE}
                      $challengeFilter ${this.searchField("c.name")}"""
      SQL(query).on('ss -> this.search(searchString)).as(int("total").single)
    }
  }

  /**
    * Gets the survey activity which includes the answers for the survey
    *
    * @param surveyId The id for the survey
    * @param start    the start date
    * @param end      the end date
    * @param priority any priority being applied
    * @return A list of challenge activities
    */
  def getSurveyActivity(surveyId: Long, start: Option[DateTime] = None, end: Option[DateTime] = None, priority: Option[Int] = None): List[ChallengeActivity] = {
    this.db.withConnection { implicit c =>
      val parser = for {
        seriesDate <- get[DateTime]("series_date")
        answer_id <- get[Option[Int]]("survey_answers.answer_id")
        answer <- get[Option[String]]("answers.answer")
        count <- int("count")
      } yield ChallengeActivity(seriesDate, answer_id.getOrElse(-2), answer.getOrElse("N/A"), count)
      val dates = this.getDates(start, end)
      SQL"""
           SELECT series_date, answer_id, answer,
              CASE WHEN count IS NULL THEN 0 ELSE count END AS count
           FROM (SELECT CURRENT_DATE + i AS series_date
                  FROM generate_series(date '#${dates._1}' - CURRENT_DATE, date '#${dates._2}' - CURRENT_DATE) i) d
                  LEFT JOIN (
                    SELECT sa.created::date, sa.answer_id, a.answer, COUNT(sa.answer_id) AS count
                    FROM survey_answers sa
                    INNER JOIN answers a ON a.id = sa.answer_id
                    #${this.getEnabledPriorityClause(false, true, start, end, priority)}
                    AND sa.survey_id = $surveyId
                    GROUP BY sa.created::date, sa.answer_id, a.answer
                    ORDER BY sa.created::date, sa.answer_id, a.answer ASC
                ) sa ON d.series_date = sa.created""".as(parser.*)
    }
  }

  /**
    * Gets the project activity (default will get data for all projects) grouped by timed action
    *
    * @param projectList The projects to filter by default None, will assume all projects
    * @param challengeId The challenge to filter by default None, if set will ignore the projects parameter
    * @param start       The start date to filter by, default None will take all values. If start set and
    *                    end not set, then will go from start till current day.
    * @param end         The end date to filter by, default None will take all values. If start not set and
    *                    end is set, then will ignore the end date
    */
  def getChallengeActivity(projectList: Option[List[Long]] = None, challengeId: Option[Long] = None,
                           start: Option[DateTime] = None, end: Option[DateTime] = None, priority: Option[Int] = None): List[ChallengeActivity] = {
    this.db.withConnection { implicit c =>
      val parser = for {
        seriesDate <- get[DateTime]("series_date")
        status <- int("status")
        count <- int("count")
      } yield ChallengeActivity(seriesDate, status, Task.getStatusName(status).getOrElse("Unknown"), count)
      val challengeProjectFilter = challengeId match {
        case Some(id) => s"AND challenge_id = $id"
        case None => buildProjectSearch(projectList, "project_id", "c.id")
      }
      val dates = this.getDates(start, end)
      SQL"""
          SELECT series_date,
            CASE WHEN sa.status IS NULL THEN 0 ELSE sa.status END AS status,
            CASE WHEN count IS NULL THEN 0 ELSE count END AS count
          FROM (SELECT CURRENT_DATE + i AS series_date
                FROM generate_series(date '#${dates._1}' - CURRENT_DATE, date '#${dates._2}' - CURRENT_DATE) i) d
          LEFT JOIN (
            SELECT sa.created::date, sa.status, COUNT(sa.status) AS count
            FROM status_actions sa
            #${this.getEnabledPriorityClause(challengeId.isEmpty, false, start, end, priority)}
            AND sa.status IN (0, 1, 2, 3, 5, 6) AND old_status != sa.status
            #$challengeProjectFilter
            GROUP BY sa.created::date, sa.status
            ORDER BY sa.created::date, sa.status ASC
          ) sa ON d.series_date = sa.created""".as(parser.*)
    }
  }

  /**
    * Gets the raw activity from the status action logs, it joins the user, projects and challenges
    * table to get the names for the various objects
    *
    * @param userFilter      A filter for users
    * @param projectFilter   A filter for projects
    * @param challengeFilter A filter for challenges
    * @param start           A filter for the start date
    * @param end             A filter for the end date
    * @return Returns a list of activity
    */
  def getRawActivity(userFilter: Option[List[Long]] = None, projectFilter: Option[List[Long]] = None, challengeFilter: Option[List[Long]] = None,
                     start: Option[DateTime] = None, end: Option[DateTime] = None): List[RawActivity] = {
    this.db.withConnection { implicit c =>
      val parser = for {
        date <- get[DateTime]("status_actions.created")
        osmUserId <- long("status_actions.osm_user_id")
        osmUsername <- str("users.name")
        projectId <- long("status_actions.project_id")
        projectName <- str("projects.name")
        challengeId <- long("status_actions.challenge_id")
        challengeName <- str("challenges.name")
        taskId <- long("status_actions.task_id")
        oldStatus <- int("status_actions.old_status")
        status <- int("status_actions.status")
      } yield RawActivity(date, osmUserId, osmUsername, projectId, projectName, challengeId,
        challengeName, taskId, oldStatus, status)

      var challengeList = challengeFilter
      if (projectFilter != None) {
        challengeList = findRelevantChallenges(projectFilter)
      }

      SQL"""
         SELECT sa.created, sa.osm_user_id, u.name, sa.project_id, p.name, sa.challenge_id,
                 c.name, sa.task_id, sa.old_status, sa.status
         FROM status_actions sa
         LEFT JOIN users u ON u.osm_id = sa.osm_user_id
         INNER JOIN projects p ON p.id = sa.project_id
         INNER JOIN challenges c ON c.id = sa.challenge_id
         WHERE #${getDateClause("sa.created", start, end)}
         #${getLongListFilter(challengeList, "sa.challenge_id")}
         #${getLongListFilter(userFilter, "sa.osm_user_id")}
         """.as(parser.*)
    }
  }

  /**
    * Gets leaderboard of top-scoring users based on task completion activity
    * over the given period. Scoring for each completed task is based on status
    * assigned to the task (status point values are configurable). Users are
    * returned in descending order with top scores first; ties are broken by
    * OSM user id with the lowest/earliest ids being ranked ahead of
    * higher/later ids. Also included with each user are their top challenges
    * (by amount of activity).
    *
    * If the optional userFilter, projectFilter or challengeFilter are given,
    * activity will be limited to those users, projects and/or challenges.
    *
    * @param userFilter        a filter for users
    * @param projectFilter     a filter for projects
    * @param challengeFilter   a filter for challenges
    * @param countryCodeFilter a filter for limiting tasks to certain countries
    * @param monthDuration     number of months to fetch
    * @param start             the start date (if not using monthDuration)
    * @param end               the end date (if not using monthDuration)
    * @param onlyEnabled       only enabled in user top challenges (doesn't affect scoring)
    * @param limit             limit the number of returned users
    * @param offset            paging, starting at 0
    * @return Returns list of leaderboard users with scores
    */
  def getUserLeaderboard(userFilter: Option[List[Long]] = None, projectFilter: Option[List[Long]] = None,
                         challengeFilter: Option[List[Long]] = None, countryCodeFilter: Option[List[String]] = None,
                         monthDuration: Option[Int] = None, start: Option[DateTime] = None, end: Option[DateTime] = None,
                         onlyEnabled: Boolean = true, limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0): List[LeaderboardUser] =
    db.withConnection { implicit c =>
      // We can attempt to use the pre-built user_leaderboard table if we have no user, project and challenge filters.
      if (userFilter == None && projectFilter == None && challengeFilter == None &&
        onlyEnabled && monthDuration != None) {

        val parser = for {
          userId <- long("user_id")
          name <- str("user_name")
          avatarURL <- str("user_avatar_url")
          score <- int("user_score")
          rank <- int("user_ranking")
        } yield LeaderboardUser(userId, name, avatarURL, score, rank,
          this.getUserTopChallenges(userId, projectFilter, challengeFilter,
            countryCodeFilter, monthDuration, start, end, onlyEnabled))

        if (countryCodeFilter == None) {
          val result =
            SQL"""SELECT *
                FROM user_leaderboard
                WHERE month_duration = ${monthDuration} AND country_code IS NULL
                ORDER BY user_ranking ASC
                LIMIT #${this.sqlLimit(limit)} OFFSET #${offset}
           """.as(parser.*)

          if (result.length > 0) {
            return result
          }
        }
        else if (countryCodeFilter.toList.head.length == 1) {
          val result =
            SQL"""SELECT *
                 FROM user_leaderboard
                 WHERE month_duration = ${monthDuration} AND
                 country_code = ${countryCodeFilter.toList.head.head}
                 ORDER BY user_ranking ASC
                 LIMIT #${this.sqlLimit(limit)} OFFSET #${offset}
            """.as(parser.*)

          if (result.length > 0) {
            return result
          }
        }
      }

      var projectList = projectFilter
      var challengeList = challengeFilter

      // Let's determine all the challenges that are in these projects
      // to make our query faster.
      if (projectList != None) {
        challengeList = findRelevantChallenges(projectList)
        projectList = None
      }

      val parser = for {
        userId <- long("users.id")
        name <- str("users.name")
        avatarURL <- str("users.avatar_url")
        score <- int("score")
        rank <- int("row_number")
      } yield LeaderboardUser(userId, name, avatarURL, score, rank,
        this.getUserTopChallenges(userId, projectList,
          challengeList, countryCodeFilter,
          monthDuration, start, end, onlyEnabled))

      var startDate = start
      var endDate = end
      if (monthDuration != None) {
        endDate = Option(new DateTime())
        if (monthDuration.get == -1) {
          startDate = Option(new DateTime(2000, 1, 1, 12, 0, 0, 0))
        }
        else {
          startDate = Option(new DateTime().minusMonths(monthDuration.get))
        }
      }

      SQL"""#${
        this.leaderboardWithRankSQL(userFilter, projectList, challengeList,
          countryCodeFilter, startDate, endDate)
      }
            LIMIT #${this.sqlLimit(limit)} OFFSET #${offset}
       """.as(parser.*)
    }

  /**
    * Returns the SQL to fetch the ordered leaderboard data with rankings. Can be filtered
    * by a list of projects, challenges, users and start/end date.
    **/
  private def leaderboardWithRankSQL(userFilter: Option[List[Long]] = None, projectFilter: Option[List[Long]] = None,
                                     challengeFilter: Option[List[Long]] = None, countryCodeFilter: Option[List[String]] = None,
                                     start: Option[DateTime] = None, end: Option[DateTime] = None): String = {
    var taskTableIfNeeded = ""
    var boundingSearch = ""

    countryCodeFilter match {
      case Some(ccList) if ccList.nonEmpty =>
        taskTableIfNeeded = ", tasks t"
        val boundingBoxes = ccList.map { cc =>
          s"""
                ST_Intersects(t.location, ST_MakeEnvelope(${boundingBoxFinder.boundingBoxforCountry(cc)}, 4326))
              """
        }
        boundingSearch = "t.id = sa.task_id AND (" + boundingBoxes.mkString(" OR ") + ") AND "
      case _ => ""
    }

    var challengeList = challengeFilter

    // Let's determine all the challenges that are in these projects
    // to make our query faster.
    if (projectFilter != None) {
      challengeList = findRelevantChallenges(projectFilter)
    }

    s"""
        SELECT users.id, users.name, users.avatar_url, ${this.scoreSumSQL()} AS score,
               ROW_NUMBER() OVER( ORDER BY ${this.scoreSumSQL()} DESC, sa.osm_user_id ASC)
        FROM status_actions sa, users
        $taskTableIfNeeded
        WHERE ${getDateClause("sa.created", start, end)} AND
              sa.old_status <> sa.status AND
              users.osm_id = sa.osm_user_id AND
              $boundingSearch
              users.leaderboard_opt_out = FALSE
              ${getLongListFilter(userFilter, "users.id")}
              ${getLongListFilter(challengeList, "sa.challenge_id")}
        GROUP BY sa.osm_user_id, users.id
        ORDER BY score DESC, sa.osm_user_id ASC
      """
  }

  /**
    * Returns the SQL to sum a user's status actions for ranking purposes
    **/
  private def scoreSumSQL(statusActionsTableName: String = "sa"): String = {
    s"""SUM(CASE ${statusActionsTableName}.status
             WHEN ${Task.STATUS_FIXED} THEN ${config.taskScoreFixed}
             WHEN ${Task.STATUS_FALSE_POSITIVE} THEN ${config.taskScoreFalsePositive}
             WHEN ${Task.STATUS_ALREADY_FIXED} THEN ${config.taskScoreAlreadyFixed}
             WHEN ${Task.STATUS_TOO_HARD} THEN ${config.taskScoreTooHard}
             WHEN ${Task.STATUS_SKIPPED} THEN ${config.taskScoreSkipped}
             ELSE 0
           END)"""
  }

  /**
    * Gets the top challenges by activity for the given user over the given period.
    * Challenges are in descending order by amount of activity, with ties broken
    * by the challenge id with the lowest/earliest ids being ranked ahead of
    * higher/later ids.
    *
    * @param userId            the id of the user
    * @param projectFilter     a filter for projects
    * @param challengeFilter   a filter for challenges
    * @param countryCodeFilter a filter for limiting tasks to certain countries
    * @param monthDuration     number of months to fetch
    * @param start             the start date (if not using monthDuration)
    * @param end               the end date (if not using monthDuration)
    * @param onlyEnabled       only get enabled challenges
    * @param limit             limit the number of returned challenges
    * @param offset            paging, starting at 0
    * @return Returns list of leaderboard challenges
    */
  def getUserTopChallenges(userId: Long, projectFilter: Option[List[Long]] = None,
                           challengeFilter: Option[List[Long]] = None, countryCodeFilter: Option[List[String]],
                           monthDuration: Option[Int] = None, start: Option[DateTime] = None, end: Option[DateTime] = None,
                           onlyEnabled: Boolean = true, limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0): List[LeaderboardChallenge] =
    db.withConnection { implicit c =>
      // We can attempt to use the pre-built user_top_challenges table if we have no project and challenge filters.
      if (projectFilter == None && challengeFilter == None && onlyEnabled && monthDuration != None) {
        val parser = for {
          id <- long("challenge_id")
          name <- str("challenge_name")
          activity <- int("activity")
        } yield LeaderboardChallenge(id, name, activity)

        val dates = getDates(start, end)

        if (countryCodeFilter == None) {
          val result =
            SQL"""SELECT challenge_id, challenge_name, activity
                FROM user_top_challenges
                WHERE user_id = #${userId} AND month_duration = ${monthDuration}
                      AND country_code IS NULL
                ORDER BY activity DESC, challenge_id ASC
                LIMIT #${this.sqlLimit(limit)} OFFSET #${offset}
           """.as(parser.*)

          if (result.length > 0) {
            return result
          }
        }
        else if (countryCodeFilter.toList.head.length == 1) {
          val result =
            SQL"""SELECT challenge_id, challenge_name, activity
                FROM user_top_challenges
                WHERE user_id = #${userId} AND month_duration = ${monthDuration}
                      AND country_code = ${countryCodeFilter.toList.head.head}
                ORDER BY activity DESC, challenge_id ASC
                LIMIT #${this.sqlLimit(limit)} OFFSET #${offset}
           """.as(parser.*)

          if (result.length > 0) {
            return result
          }
        }
      }

      val enabledFilter = if (onlyEnabled) "AND p.id = sa.project_id AND c.enabled = TRUE and p.enabled = TRUE" else ""

      val parser = for {
        id <- long("status_actions.challenge_id")
        name <- str("challenges.name")
        activity <- int("activity")
      } yield LeaderboardChallenge(id, name, activity)

      var startDate = start
      var endDate = end
      if (monthDuration != None) {
        endDate = Option(new DateTime())
        if (monthDuration.get == -1) {
          startDate = Option(new DateTime(2000, 1, 1, 12, 0, 0, 0))
        }
        else {
          startDate = Option(new DateTime().minusMonths(monthDuration.get))
        }
      }

      var taskTableIfNeeded = ""
      var boundingSearch = ""
      countryCodeFilter match {
        case Some(ccList) if ccList.nonEmpty =>
          taskTableIfNeeded = ", tasks t"
          val boundingBoxes = ccList.map { cc =>
            s"""
                  ST_Intersects(t.location, ST_MakeEnvelope(${boundingBoxFinder.boundingBoxforCountry(cc)}, 4326))
                """
          }
          boundingSearch = "t.id = sa.task_id AND (" + boundingBoxes.mkString(" OR ") + ") AND "
        case _ => ""
      }

      var challengeList = challengeFilter

      // Let's determine all the challenges that are in these projects
      // to make our query faster.
      if (projectFilter != None) {
        challengeList = findRelevantChallenges(projectFilter)
      }

      SQL"""SELECT sa.challenge_id, c.name, count(sa.challenge_id) as activity
            FROM status_actions sa, challenges c, projects p, users u
            #${taskTableIfNeeded}
            WHERE #${getDateClause("sa.created", startDate, endDate)} AND
                  u.id = #${userId} AND
                  sa.osm_user_id = u.osm_id AND
                  #${boundingSearch}
                  sa.challenge_id = c.id
                  #${getLongListFilter(challengeList, "sa.challenge_id")}
                  #${enabledFilter}
            GROUP BY sa.challenge_id, c.name
            ORDER BY activity DESC, sa.challenge_id ASC
            LIMIT #${this.sqlLimit(limit)} OFFSET #${offset}
       """.as(parser.*)
    }

  /**
    * Gets leaderboard rank for a user based on task completion activity
    * over the given period. Scoring for each completed task is based on status
    * assigned to the task (status point values are configurable). Also included
    * is the user's top challenges (by amount of activity).
    *
    * If the optional projectFilter or challengeFilter are given,
    * activity will be limited to those projects and/or challenges.
    *
    * @param userId            user id
    * @param projectFilter     a filter for projects
    * @param challengeFilter   a filter for challenges
    * @param countryCodeFilter a filter for limiting tasks to certain countries
    * @param monthDuration     number of months to fetch
    * @param start             the start date (if not using monthDuration)
    * @param end               the end date (if not using monthDuration)
    * @param onlyEnabled       only enabled in user top challenges (doesn't affect scoring)
    * @param bracket           the number of users to also return who rank above and below userId
    * @return Returns leaderboard for user with score
    */
  def getLeaderboardForUser(userId: Long, projectFilter: Option[List[Long]] = None,
                            challengeFilter: Option[List[Long]] = None, countryCodeFilter: Option[List[String]],
                            monthDuration: Option[Int] = None, start: Option[DateTime] = None, end: Option[DateTime] = None,
                            onlyEnabled: Boolean = true, bracket: Int = 0): List[LeaderboardUser] = {
    db.withConnection { implicit c =>
      // We can attempt to use the pre-built user_leaderboard table if we have no project and challenge filters.
      if (projectFilter == None && challengeFilter == None && onlyEnabled && monthDuration != None) {
        val parser = for {
          userId <- long("user_id")
          name <- str("user_name")
          avatarURL <- str("user_avatar_url")
          score <- int("user_score")
          rank <- int("user_ranking")
        } yield LeaderboardUser(userId, name, avatarURL, score, rank,
          this.getUserTopChallenges(userId, projectFilter, challengeFilter,
            countryCodeFilter, monthDuration, start, end, onlyEnabled))

        if (countryCodeFilter == None) {
          val result =
            SQL"""
              WITH rankVariable (rankNum) as (
                SELECT user_ranking FROM user_leaderboard
                WHERE user_id = #${userId} AND month_duration = ${monthDuration}
                AND country_code IS NULL
              )

              SELECT * FROM user_leaderboard, rankVariable
                  WHERE month_duration = ${monthDuration} AND country_code IS NULL AND
                  user_ranking BETWEEN (rankNum - #${bracket}) AND (rankNum + #${bracket})
             """.as(parser.*)

          if (result.length > 0) {
            return result
          }
        }
        else if (countryCodeFilter.toList.head.length == 1) {
          val result =
            SQL"""
              WITH rankVariable (rankNum) as (
                SELECT user_ranking FROM user_leaderboard
                WHERE user_id = #${userId} AND month_duration = ${monthDuration}
                      AND country_code = ${countryCodeFilter.toList.head.head}
              )

              SELECT * FROM user_leaderboard, rankVariable
                WHERE month_duration = ${monthDuration} AND
                      country_code = ${countryCodeFilter.toList.head.head} AND
                      user_ranking BETWEEN (rankNum - #${bracket}) AND (rankNum + #${bracket})
             """.as(parser.*)

          if (result.length > 0) {
            return result
          }
        }
      }

      val parser = for {
        userId <- long("users.id")
        name <- str("users.name")
        avatarURL <- str("users.avatar_url")
        score <- int("score")
        rank <- int("row_number")
      } yield LeaderboardUser(userId, name, avatarURL, score, rank,
        this.getUserTopChallenges(userId, projectFilter, challengeFilter,
          countryCodeFilter, monthDuration, start, end, onlyEnabled))

      var startDate = start
      var endDate = end
      if (monthDuration != None) {
        endDate = Option(new DateTime())
        if (monthDuration.get == -1) {
          startDate = Option(new DateTime(2000, 1, 1, 12, 0, 0, 0))
        }
        else {
          startDate = Option(new DateTime().minusMonths(monthDuration.get))
        }
      }

      val sqlSelectWithRank = this.leaderboardWithRankSQL(None, projectFilter,
        challengeFilter, countryCodeFilter, startDate, endDate)

      SQL"""WITH rankVariable (rankNum) as (
          SELECT row_number FROM (#${sqlSelectWithRank}) user_rank
          WHERE id = #${userId}
        )

        SELECT * FROM (#${sqlSelectWithRank}) ranks, rankVariable
            WHERE row_number BETWEEN (rankNum - #${bracket}) AND (rankNum + #${bracket})
         """.as(parser.*)
    }
  }

  /**
    * Gets the most recent activity entries for each challenge, regardless of date.
    *
    * @param projectIds   restrict to specified projects
    * @param challengeIds restrict to specified challenges
    * @param entries      the number of most recent activity entries per challenge. Defaults to 1.
    * @return most recent activity entries for each challenge
    */
  def getLatestChallengeActivity(projectFilter: Option[List[Long]] = None, challengeFilter: Option[List[Long]] = None, entries: Int = 1): List[RawActivity] = {
    db.withConnection { implicit c =>
      val parser = for {
        date <- get[DateTime]("status_actions.created")
        osmUserId <- long("status_actions.osm_user_id")
        osmUsername <- str("users.name")
        projectId <- long("status_actions.project_id")
        projectName <- str("projects.name")
        challengeId <- long("status_actions.challenge_id")
        challengeName <- str("challenges.name")
        taskId <- long("status_actions.task_id")
        oldStatus <- int("status_actions.old_status")
        status <- int("status_actions.status")
      } yield RawActivity(date, osmUserId, osmUsername, projectId, projectName, challengeId,
        challengeName, taskId, oldStatus, status)

      var challengeList = challengeFilter

      // Let's determine all the challenges that are in these projects
      // to make our query faster.
      if (projectFilter != None) {
        challengeList = findRelevantChallenges(projectFilter)
      }

      SQL"""SELECT sa.*, challenges.name, projects.name, users.name FROM challenges, projects, users
            JOIN LATERAL (
              SELECT * FROM status_actions
              WHERE challenge_id = challenges.id
              AND old_status <> status
              ORDER BY created DESC
              LIMIT ${entries}
            ) sa ON true
            WHERE challenges.parent_id = projects.id
            AND users.osm_id = sa.osm_user_id
            #${getLongListFilter(challengeList, "challenges.id")}
      """.as(parser.*)
    }
  }
}

object DataManager {
  val PREVIOUS_WEEK = -7
}
