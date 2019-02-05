package org.maproulette.jobs.utils

import java.time.LocalDate;
import java.time.Month;
import java.time.Period;
import java.time.format.DateTimeFormatter;

import org.maproulette.models.Task
import org.maproulette.Config

/**
  *
  *
  * @author krotstan
  */
object LeaderboardHelper {
  /**
   * Returns the SQL to rebuild the Challenges Leaderboard table with the
   * given dates
   *
   * @param start_date
   * @param end_date
   */
  def rebuildChallengesLeaderboardSQL(monthDuration:Integer, config:Config) : String = {
    val today = LocalDate.now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val startMonth = LocalDate.now.minus(Period.ofMonths(monthDuration)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    s"""INSERT INTO user_leaderboard
        (month_duration, user_id, user_name, user_avatar_url, user_ranking, user_score)
        SELECT $monthDuration, users.id, users.name, users.avatar_url,
                ROW_NUMBER() OVER( ORDER BY ${this.scoreSumSQL(config)} DESC, sa.osm_user_id ASC),
                ${this.scoreSumSQL(config)} AS score
                FROM status_actions sa, users
                WHERE sa.created::DATE BETWEEN '$startMonth' AND '$today' AND
                      sa.old_status <> sa.status AND
                      users.osm_id = sa.osm_user_id AND
                      users.leaderboard_opt_out = FALSE
                GROUP BY sa.osm_user_id, users.id
                ORDER BY score DESC, sa.osm_user_id ASC"""
  }

  /**
   * Returns the SQL to rebuild the Challenges Leaderboard table with the
   * given dates
   *
   * @param start_date
   * @param end_date
   */
  def rebuildChallengesLeaderboardSQLCountry(monthDuration:Integer, countryCode:String,
                                             boundingBox:String, config:Config) : String = {
    val today = LocalDate.now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val startMonth = LocalDate.now.minus(Period.ofMonths(monthDuration)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    s"""INSERT INTO user_leaderboard
        (month_duration, country_code, user_id, user_name, user_avatar_url, user_ranking, user_score)
        SELECT $monthDuration, '${countryCode}', users.id, users.name, users.avatar_url,
                ROW_NUMBER() OVER( ORDER BY ${this.scoreSumSQL(config)} DESC, sa.osm_user_id ASC),
                ${this.scoreSumSQL(config)} AS score
                FROM status_actions sa, users, tasks t
                WHERE sa.created::DATE BETWEEN '$startMonth' AND '$today' AND
                      sa.old_status <> sa.status AND
                      users.osm_id = sa.osm_user_id AND
                      users.leaderboard_opt_out = FALSE AND
                      t.id = sa.task_id AND
                      ST_Intersects(t.location, ST_MakeEnvelope(${boundingBox}, 4326))
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
  def rebuildTopChallengesSQL(monthDuration:Integer, config:Config) : String = {
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
   * Returns the SQL to rebuild the Top Challenges table with the
   * given dates
   *
   * @param start_date
   * @param end_date
   */
  def rebuildTopChallengesSQLCountry(monthDuration:Integer, countryCode: String,
                                     boundingBox: String, config:Config) : String = {
    val today = LocalDate.now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val startMonth = LocalDate.now.minus(Period.ofMonths(monthDuration)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    s"""INSERT INTO user_top_challenges
        (month_duration, country_code, user_id, challenge_id, challenge_name, activity)
        SELECT $monthDuration, '$countryCode', u.id, sa.challenge_id, c.name, count(sa.challenge_id) as activity
          FROM status_actions sa, challenges c, projects p, users u, tasks t
          WHERE sa.created::DATE BETWEEN '$startMonth' AND '$today' AND
            sa.osm_user_id = u.osm_id AND sa.challenge_id = c.id AND
            p.id = sa.project_id AND c.enabled = TRUE and p.enabled = TRUE AND
            t.id = sa.task_id AND
            ST_Intersects(t.location, ST_MakeEnvelope(${boundingBox}, 4326))
          GROUP BY sa.challenge_id, c.name, u.id"""
  }

  /**
   * Returns the SQL to sum a user's status actions for ranking purposes
   **/
  def scoreSumSQL(config:Config) : String = {
    s"""SUM(CASE sa.status
             WHEN ${Task.STATUS_FIXED} THEN ${config.taskScoreFixed}
             WHEN ${Task.STATUS_FALSE_POSITIVE} THEN ${config.taskScoreFalsePositive}
             WHEN ${Task.STATUS_ALREADY_FIXED} THEN ${config.taskScoreAlreadyFixed}
             WHEN ${Task.STATUS_TOO_HARD} THEN ${config.taskScoreTooHard}
             WHEN ${Task.STATUS_SKIPPED} THEN ${config.taskScoreSkipped}
             ELSE 0
           END)"""
  }
}
