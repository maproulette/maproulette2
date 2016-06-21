package org.maproulette.data

import java.sql.Connection
import java.text.SimpleDateFormat
import java.util.{Calendar, Date}
import javax.inject.{Inject, Singleton}

import anorm._
import anorm.SqlParser._
import org.maproulette.Config
import org.maproulette.actions.Actions
import org.maproulette.models.Task
import org.maproulette.models.utils.DALHelper
import play.api.Application
import play.api.db.Database

case class ActionSummary(total:Double,
                          available:Double,
                             fixed:Double,
                             falsePositive:Double,
                             skipped:Double,
                             deleted:Double,
                             alreadyFixed:Double,
                             tooHard:Double) {
  def percentComplete = (((available / total) * 100) - 100) * -1
  def percentage(value:Double) = (value / total) * 100
}

/**
  * Handles the summary data for the users
  *
  * @param distinctAllUsers This is all the distinct users regardless of the date range
  * @param distinctTotalUsers All the distinct users within the supplied date range
  * @param avgUsersPerChallenge Average number of users per challenge within the date range
  * @param activeUsers Active users (2 or more edits in last 2 days)
  * @param avgActionsPerUser Average number of actions taken by a user
  * @param avgActionsPerChallengePerUser Average number of actions taken by user per challenge
  */
case class UserSummary(distinctAllUsers:Int,
                       distinctTotalUsers:Int,
                       avgUsersPerChallenge:Double,
                       activeUsers:Double,
                       avgActionsPerUser:ActionSummary,
                       avgActionsPerChallengePerUser:ActionSummary)
case class UserSurveySummary(distinctTotalUsers:Int,
                             avgUsersPerChallenge:Double,
                             activeUsers:Double,
                             answerPerUser:Double,
                             answersPerChallenge:Double)
case class ChallengeSummary(id:Long, name:String, actions:ActionSummary)
case class ChallengeActivity(date:Date, status:Int, statusName:String, count:Int)

/**
  * @author cuthbertm
  */
@Singleton
class DataManager @Inject()(config: Config, db:Database)(implicit application:Application) extends DALHelper {
  private val dateFormat = new SimpleDateFormat("yyyy-MM-dd")

  private def getDistinctUsers(projectFilter:String, survey:Boolean=false, onlyEnabled:Boolean=true,
                               start:Option[Date]=None, end:Option[Date]=None)(implicit c:Connection) : Int = {
    SQL"""SELECT COUNT(DISTINCT osm_user_id) AS count
             FROM #${if (survey) {"survey_answers sa"} else {"status_actions sa"}}
             #${getEnabledClause(onlyEnabled, survey, start, end)}
             #$projectFilter""".as(get[Option[Int]]("count").single).getOrElse(0)
  }

  private def getDistinctUsersPerChallenge(projectFilter:String, survey:Boolean=false, onlyEnabled:Boolean=true,
                                           start:Option[Date]=None, end:Option[Date]=None)(implicit c:Connection) : Double = {
    SQL"""SELECT AVG(count) AS count FROM (
            SELECT #${if (survey) {"survey_id"} else {"challenge_id"}}, COUNT(DISTINCT osm_user_id) AS count
            FROM #${if (survey) {"survey_answers sa"} else {"status_actions sa"}}
            #${getEnabledClause(onlyEnabled, survey, start, end)} #$projectFilter
            GROUP BY #${if (survey) {"survey_id"} else {"challenge_id"}}
          ) as t""".as(get[Option[Double]]("count").single).getOrElse(0D)
  }

  private def getActiveUsers(projectFilter:String, survey:Boolean=false, onlyEnabled:Boolean=true,
                             start:Option[Date]=None, end:Option[Date]=None)(implicit c:Connection) : Int = {
    SQL"""SELECT COUNT(DISTINCT osm_user_id) AS count
          FROM #${if (survey) {"survey_answers sa"} else {"status_actions sa"}}
          #${getEnabledClause(onlyEnabled, survey, start, end)}  #$projectFilter
          AND sa.created::date BETWEEN current_date - INTERVAL '2 days' AND current_date"""
      .as(get[Option[Int]]("count").single).getOrElse(0)
  }

  def getUserSurveySummary(projectList:Option[List[Long]]=None, surveyId:Option[Long]=None,
                           start:Option[Date]=None, end:Option[Date]=None) = {
    db.withConnection { implicit c =>
      val surveyProjectFilter = surveyId match {
        case Some(id) => s"AND survey_id = $id"
        case None => projectList match {
          case Some(pl) => s"AND project_id IN (${pl.mkString(",")})"
          case None => ""
        }
      }

      val perUser:Double = SQL"""SELECT AVG(answered) AS answered FROM (
                            SELECT osm_user_id, COUNT(DISTINCT answer_id) AS answered
                            FROM survey_answers sa
                            #${getEnabledClause(surveyId.isEmpty, true, start, end)}
                            #$surveyProjectFilter
                            GROUP BY osm_user_id
                          ) AS t""".as(get[Option[Double]]("answered").single).getOrElse(0)
      val perSurvey:Double = SQL"""SELECT AVG(answered) AS answered FROM (
                              SELECT osm_user_id, survey_id, COUNT(DISTINCT answer_id) AS answered
                              FROM survey_answers
                              #${getEnabledClause(surveyId.isEmpty, true, start, end)}
                              #$surveyProjectFilter
                              GROUP BY osm_user_id, survey_id
                            ) AS t""".as(get[Option[Double]]("answered").single).getOrElse(0)

      UserSurveySummary(getDistinctUsers(surveyProjectFilter, true, surveyId.isEmpty, start, end),
        getDistinctUsersPerChallenge(surveyProjectFilter, true, surveyId.isEmpty, start, end),
        getActiveUsers(surveyProjectFilter, true, surveyId.isEmpty, start, end),
        perUser,
        perSurvey
      )
    }
  }

  def getUserChallengeSummary(projectList:Option[List[Long]]=None, challengeId:Option[Long]=None,
                              start:Option[Date]=None, end:Option[Date]=None) : UserSummary = {
    db.withConnection { implicit c =>
      val challengeProjectFilter = challengeId match {
        case Some(id) => s"AND sa.challenge_id = $id"
        case None => projectList match {
          case Some(pl) => s"AND sa.project_id IN (${pl.mkString(",")})"
          case None => ""
        }
      }
      val actionParser = for {
        available <- get[Option[Double]]("available")
        fixed <- get[Option[Double]]("fixed")
        falsePositive <- get[Option[Double]]("false_positive")
        skipped <- get[Option[Double]]("skipped")
        deleted <- get[Option[Double]]("deleted")
        alreadyFixed <- get[Option[Double]]("already_fixed")
        tooHard <- get[Option[Double]]("too_hard")
      } yield ActionSummary(0, available.getOrElse(0), fixed.getOrElse(0), falsePositive.getOrElse(0),
                            skipped.getOrElse(0), deleted.getOrElse(0), alreadyFixed.getOrElse(0), tooHard.getOrElse(0))

      val perUser = SQL"""SELECT AVG(available) AS available, AVG(fixed) AS fixed, AVG(false_positive) AS false_positive,
                                  AVG(skipped) AS skipped, AVG(deleted) AS deleted, AVG(already_fixed) AS already_fixed,
                                  AVG(too_hard) AS too_hard
                              FROM (
                                SELECT sa.osm_user_id,
                                    SUM(CASE sa.status WHEN 0 THEN 1 ELSE 0 END) AS available,
                                    SUM(CASE sa.status WHEN 1 THEN 1 ELSE 0 END) AS fixed,
                                    SUM(CASE sa.status WHEN 2 THEN 1 ELSE 0 END) AS false_positive,
                                    SUM(CASE sa.status WHEN 3 THEN 1 ELSE 0 END) AS skipped,
                                    SUM(CASE sa.status WHEN 4 THEN 1 ELSE 0 END) AS deleted,
                                    SUM(CASE sa.status WHEN 5 THEN 1 ELSE 0 END) AS already_fixed,
                                    SUM(CASE sa.status WHEN 6 THEN 1 ELSE 0 END) AS too_hard
                                FROM status_actions sa
                                #${getEnabledClause(challengeId.isEmpty, false, start, end)}
                                #$challengeProjectFilter
                                GROUP BY sa.osm_user_id
                              ) AS t""".as(actionParser.*).head
      val perChallenge = SQL"""SELECT AVG(available) AS available, AVG(fixed) AS fixed, AVG(false_positive) AS false_positive,
                                        AVG(skipped) AS skipped, AVG(deleted) AS deleted, AVG(already_fixed) AS already_fixed,
                                        AVG(too_hard) as too_hard
                                  FROM (
                                    SELECT osm_user_id, challenge_id,
                                        SUM(CASE sa.status WHEN 0 THEN 1 ELSE 0 END) AS available,
                                        SUM(CASE sa.status WHEN 1 THEN 1 ELSE 0 END) AS fixed,
                                        SUM(CASE sa.status WHEN 2 THEN 1 ELSE 0 END) AS false_positive,
                                        SUM(CASE sa.status WHEN 3 THEN 1 ELSE 0 END) AS skipped,
                                        SUM(CASE sa.status WHEN 4 THEN 1 ELSE 0 END) AS deleted,
                                        SUM(CASE sa.status WHEN 5 THEN 1 ELSE 0 END) AS already_fixed,
                                        SUM(CASE sa.status WHEN 6 THEN 1 ELSE 0 END) AS too_hard
                                    FROM status_actions sa
                                    #${getEnabledClause(challengeId.isEmpty, false, start, end)}
                                    #$challengeProjectFilter
                                    GROUP BY osm_user_id, challenge_id
                                  ) AS t""".as(actionParser.*).head
      val allUsers =
        SQL"""SELECT count(DISTINCT osm_user_id) FROM status_actions sa
              #${getEnabledClause(challengeId.isEmpty, false, None, None, true)}
              #$challengeProjectFilter
           """.as(int(1).single)
      UserSummary(allUsers,
        getDistinctUsers(challengeProjectFilter, false, challengeId.isEmpty, start, end),
        getDistinctUsersPerChallenge(challengeProjectFilter, false, challengeId.isEmpty, start, end),
        getActiveUsers(challengeProjectFilter, false, challengeId.isEmpty, start, end),
        perUser,
        perChallenge)
    }
  }

  /**
    * Gets the summarized challenge activity
    *
    * @param projectList The projects to filter by default None, will assume all projects
    * @param challengeId The challenge to filter by default None, if set will ignore the projects parameter
    * @return
    */
  def getChallengeSummary(projectList:Option[List[Long]]=None, challengeId:Option[Long]=None,
                          limit:Int=(-1), offset:Int=0, orderColumn:Option[String]=None, orderDirection:String="ASC",
                          searchString:String="") : List[ChallengeSummary] = {
    db.withConnection { implicit c =>
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
      } yield ChallengeSummary(id, name, ActionSummary(total, available, fixed, falsePositive, skipped, deleted, alreadyFixed, tooHard))
      val challengeFilter = challengeId match {
        case Some(id) if id != -1 => s"AND t.parent_id = $id"
        case _ => projectList match {
          case Some(pl) => s"AND c.parent_id IN (${pl.mkString(",")})"
          case None => ""
        }
      }
      // The percentage columns are a bit of a hack simply so that we can order by the percentages.
      // It won't decrease performance as this is simple basic math calculations, but it certainly
      // isn't pretty
      val query = s"""SELECT *,
                        (((CAST(available AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100)-100)*1 AS complete_percentage,
                        (CAST(available AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS available_perc,
                        (CAST(fixed AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS fixed_perc,
                        (CAST(false_positive AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS false_positive_perc,
                        (CAST(skipped AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS skipped_perc,
                        (CAST(already_fixed AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS already_fixed_perc,
                        (CAST(too_hard AS DOUBLE PRECISION)/CAST(total AS DOUBLE PRECISION))*100 AS too_hard_perc
                      FROM (
                      SELECT t.parent_id, c.name,
                                SUM(CASE WHEN t.status != 4 THEN 1 ELSE 0 END) as total,
                                SUM(CASE t.status WHEN 0 THEN 1 ELSE 0 END) as available,
                                SUM(CASE t.status WHEN 1 THEN 1 ELSE 0 END) as fixed,
                                SUM(CASE t.status WHEN 2 THEN 1 ELSE 0 END) as false_positive,
                                SUM(CASE t.status WHEN 3 THEN 1 ELSE 0 END) as skipped,
                                SUM(CASE t.status WHEN 4 THEN 1 ELSE 0 END) as deleted,
                                SUM(CASE t.status WHEN 5 THEN 1 ELSE 0 END) as already_fixed,
                                SUM(CASE t.status WHEN 6 THEN 1 ELSE 0 END) AS too_hard
                              FROM tasks t
                              INNER JOIN challenges c ON c.id = t.parent_id
                              INNER JOIN projects p ON p.id = c.parent_id
                              WHERE ${if(challengeId.isEmpty) {"c.enabled = true AND p.enabled = true AND"} else {""}}
                              challenge_type = ${Actions.ITEM_TYPE_CHALLENGE} $challengeFilter
                              ${searchField("c.name")}
                              GROUP BY t.parent_id, c.name
                    ) AS t
                    ${order(orderColumn, orderDirection)}
                    LIMIT ${sqlLimit(limit)} OFFSET {offset}
        """
        SQL(query).on('ss -> search(searchString), 'offset -> offset).as(parser.*)
    }
  }

  /**
    * Should be used in conjunction with challenge summary to retrieve the total number of challenges that
    * are set for the particular summary query
    *
    * @param projectList The projects that are being used to filter the results, optional
    * @param challengeId The challenge used to filter the results, optional
    * @param searchString The search string that was applied to the query
    * @return A integer value which is the total challenges included in the results
    */
  def getTotalSummaryCount(projectList:Option[List[Long]]=None, challengeId:Option[Long]=None, searchString:String="") : Int = {
    db.withConnection { implicit c =>
      val challengeFilter = challengeId match {
        case Some(id) if id != -1 => s"AND id = $id"
        case _ => projectList match {
          case Some(pl) => s"AND parent_id IN (${pl.mkString(",")})"
          case None => ""
        }
      }
      val query = s"""SELECT COUNT(*) AS total FROM challenges c
                      INNER JOIN projects p ON p.id = c.parent_id
                      WHERE ${if(challengeId.isEmpty) {"c.enabled = true AND p.enabled = true AND"} else {""}}
                      challenge_type = ${Actions.ITEM_TYPE_CHALLENGE}
                      $challengeFilter ${searchField("c.name")}"""
      SQL(query).on('ss -> search(searchString)).as(int("total").single)
    }
  }

  /**
    * Gets the project activity (default will get data for all projects) grouped by timed action
    *
    * @param projectList The projects to filter by default None, will assume all projects
    * @param challengeId The challenge to filter by default None, if set will ignore the projects parameter
    * @param start The start date to filter by, default None will take all values. If start set and
    *              end not set, then will go from start till current day.
    * @param end The end date to filter by, default None will take all values. If start not set and
    *            end is set, then will ignore the end date
    */
  def getChallengeActivity(projectList:Option[List[Long]]=None, challengeId:Option[Long]=None,
                         start:Option[Date]=None, end:Option[Date]=None) : List[ChallengeActivity] = {
    db.withConnection { implicit c =>
      val parser = for {
        seriesDate <- date("series_date")
        status <- int("status")
        count <- int("count")
      } yield ChallengeActivity(seriesDate, status, Task.getStatusName(status).getOrElse("Unknown"), count)
      val challengeProjectFilter = challengeId match {
        case Some(id) => s"AND challenge_id = $id"
        case None => projectList match {
          case Some(pl) => s"AND project_id IN (${pl.mkString(",")})"
          case None => ""
        }
      }
      val dates = getDates(start, end)
      SQL"""
          SELECT series_date,
	          CASE WHEN sa.status IS NULL THEN 0 ELSE sa.status END AS status,
	          CASE WHEN count IS NULL THEN 0 ELSE count END AS count
          FROM (SELECT CURRENT_DATE + i AS series_date
	              FROM generate_series(date '#${dates._1}' - CURRENT_DATE, date '#${dates._2}' - CURRENT_DATE) i) d
          LEFT JOIN (
	          SELECT sa.created::date, sa.status, COUNT(sa.status) AS count
            FROM status_actions sa
            #${getEnabledClause(challengeId.isEmpty, false, start, end)}
	          AND sa.status IN (0, 1, 2, 3, 5, 6) AND old_status != sa.status
            #$challengeProjectFilter
	          GROUP BY sa.created::date, sa.status
            ORDER BY sa.created::date, sa.status ASC
          ) sa ON d.series_date = sa.created""".as(parser.*)
    }
  }

  private def getDates(start:Option[Date]=None, end:Option[Date]=None) : (String, String) = {
    val startDate = start match {
      case Some(s) => dateFormat.format(s)
      case None =>
        val cal = Calendar.getInstance()
        cal.add(Calendar.DATE, -7)
        dateFormat.format(cal.getTime)
    }
    val endDate = end match {
      case Some(e) => dateFormat.format(e)
      case None => dateFormat.format(new Date())
    }
    (startDate, endDate)
  }

  private def getDateClause(start:Option[Date]=None, end:Option[Date]=None, prefix:String="",
                            clausePrefix:String="WHERE") : String = {
    val dates = getDates(start, end)
    s"$clausePrefix $prefix.created::date BETWEEN '${dates._1}' AND '${dates._2}'"
  }

  private def getEnabledClause(onlyEnabled:Boolean=true, isSurvey:Boolean=true,
                               start:Option[Date]=None, end:Option[Date]=None,
                               ignoreDates:Boolean=false) : String = {
    if (onlyEnabled) {
      s"""|INNER JOIN challenges c ON c.id = sa.${if (isSurvey) {"survey_id"} else {"challenge_id"}}
          |INNER JOIN projects p ON p.id = c.parent_id
          |WHERE c.enabled = true and p.enabled = true
          |${if(!ignoreDates) {getDateClause(start, end, "sa", "AND")} else {""}}
       """.stripMargin
    } else if (!ignoreDates) {
      getDateClause(start, end, "sa")
    } else {
      "WHERE 1=1"
    }
  }
}
