// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.controllers.api

import javax.inject.Inject
import org.maproulette.Config
import org.maproulette.data._
import org.maproulette.models.Challenge
import org.maproulette.models.dal.ChallengeDAL
import org.maproulette.session.SessionManager
import org.maproulette.utils.Utils
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.mvc._
import play.api.libs.json.JodaWrites._

import scala.util.Try

/**
  * @author cuthbertm
  */
class DataController @Inject()(sessionManager: SessionManager, challengeDAL: ChallengeDAL,
                               dataManager: DataManager, config: Config,
                               actionManager: ActionManager,
                               components: ControllerComponents,
                               statusActionManager: StatusActionManager) extends AbstractController(components) {

  implicit val actionWrites = actionManager.actionItemWrites
  implicit val dateWrites = Writes.dateWrites("yyyy-MM-dd")
  implicit val dateTimeWrites = JodaWrites.jodaDateWrites("yyyy-MM-dd'T'HH:mm:ss")
  implicit val userSurveySummaryWrites = Json.writes[UserSurveySummary]
  implicit val actionSummaryWrites = Json.writes[ActionSummary]
  implicit val userSummaryWrites = Json.writes[UserSummary]
  implicit val challengeLeaderboardWrites = Json.writes[LeaderboardChallenge]
  implicit val userLeaderboardWrites = Json.writes[LeaderboardUser]
  implicit val challengeSummaryWrites = Json.writes[ChallengeSummary]
  implicit val challengeActivityWrites = Json.writes[ChallengeActivity]
  implicit val rawActivityWrites = Json.writes[RawActivity]
  implicit val statusActionItemWrites = Json.writes[StatusActionItem]
  implicit val statusActionSummaryWrites = Json.writes[DailyStatusActionSummary]
  implicit val surveySummaryWrites = Json.writes[SurveySummary]

  implicit val stringIntMap: Writes[Map[String, Int]] = new Writes[Map[String, Int]] {
    def writes(map: Map[String, Int]): JsValue =
      Json.obj(map.map { case (s, i) =>
        val ret: (String, JsValueWrapper) = s.toString -> JsNumber(i)
        ret
      }.toSeq: _*)
  }

  /**
    * Gets the recent activity for a user
    *
    * @param limit  the limit on the number of activities return
    * @param offset paging, starting at 0
    * @return List of action summaries associated with the user
    */
  def getRecentUserActivity(limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    val actualLimit = if (limit == -1) {
      this.config.numberOfActivities
    } else {
      limit
    }
    this.sessionManager.authenticatedRequest { user =>
      Ok(Json.toJson(this.actionManager.getRecentActivity(user, actualLimit, offset)))
    }
  }

  def getUserChallengeSummary(challengeId: Long, start: String, end: String, survey: Int, priority: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (survey == 1) {
        Ok(Json.toJson(this.dataManager.getUserSurveySummary(None, Some(challengeId), Utils.getDate(start), Utils.getDate(end), this.getPriority(priority))))
      } else {
        Ok(Json.toJson(this.dataManager.getUserChallengeSummary(None, Some(challengeId), Utils.getDate(start), Utils.getDate(end), this.getPriority(priority))))
      }
    }
  }

  def getUserSummary(projects: String, start: String, end: String, survey: Int, priority: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (survey == 1) {
        Ok(Json.toJson(this.dataManager.getUserSurveySummary(
          Utils.toLongList(projects), None, Utils.getDate(start), Utils.getDate(end), this.getPriority(priority)
        )))
      } else {
        Ok(Json.toJson(this.dataManager.getUserChallengeSummary(
          Utils.toLongList(projects), None, Utils.getDate(start), Utils.getDate(end), this.getPriority(priority)
        )))
      }
    }
  }

  /**
    * Gets the top scoring users, based on task completion, over the given
    * number of montns (or using start and end dates). Included with each user is their top challenges
    * (by amount of activity).
    *
    * @param userIds       restrict to specified users
    * @param projectIds    restrict to specified projects
    * @param challengeIds  restrict to specified challenges
    * @param countryCodes  restrict tasks to specified countries
    * @param monthDuration number of months to fetch (do not include if using start/end dates)
    * @param start         the start date (if not using monthDuration)
    * @param end           the end date (if not using monthDuration)
    * @param limit         the limit on the number of users returned
    * @param onlyEnabled   only include enabled in user top challenges (doesn't affect scoring)
    * @param offset        paging, starting at 0
    * @return Top-ranked users with scores based on task completion activity
    */
  def getUserLeaderboard(userIds: String, projectIds: String, challengeIds: String, countryCodes: String,
                         monthDuration: String, start: String, end: String,
                         onlyEnabled: Boolean, limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.dataManager.getUserLeaderboard(
        Utils.toLongList(userIds), Utils.toLongList(projectIds),
        Utils.toLongList(challengeIds), Utils.toStringList(countryCodes),
        Try(monthDuration.toInt).toOption, Utils.getDate(start), Utils.getDate(end), onlyEnabled, limit, offset
      )))
    }
  }

  /**
    * Gets the leaderboard ranking for a user, based on task completion, over
    * the given number of months (or start and end dates). Included with the user is their top challenges
    * (by amount of activity). Also a bracketing number of users above and below
    * the user in the rankings.
    *
    * @param userId        user Id for user
    * @param projectIds    restrict to specified projects
    * @param challengeIds  restrict to specified challenges
    * @param countryCodes  restrict tasks to specified countries
    * @param monthDuration number of months to fetch (do not include if using start and end dates)
    * @param start         the start date (if not using monthDuration)
    * @param end           the end date (if not using monthDuration)
    * @param onlyEnabled   only include enabled in user top challenges (doesn't affect scoring)
    * @param bracket       the number of users to return above and below the given user (0 returns just the user)
    * @return User with score and ranking based on task completion activity
    */
  def getLeaderboardForUser(userId: Long, projectIds: String, challengeIds: String, countryCodes: String,
                            monthDuration: String, start: String, end: String, onlyEnabled: Boolean, bracket: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.dataManager.getLeaderboardForUser(
        userId, Utils.toLongList(projectIds), Utils.toLongList(challengeIds), Utils.toStringList(countryCodes),
        Try(monthDuration.toInt).toOption, Utils.getDate(start), Utils.getDate(end), onlyEnabled, bracket
      )))
    }
  }

  /**
    * Gets the user's top challenges, based on activity, over the given number of months
    * (or start and end dates).
    *
    * @param userId        the id of the user
    * @param projectIds    restrict to specified projects
    * @param challengeIds  restrict to specified challenges
    * @param countryCodes  restrict tasks to specified countries
    * @param monthDuration number of months (do not include if using start/end dates)
    * @param start         the start date (if not using monthDuration)
    * @param end           the end date (if not using monthDuration)
    * @param onlyEnabled   only get enabled challenges
    * @param limit         the limit on the number of challenges returned
    * @param offset        paging, starting at 0
    * @return Top challenges based on user's activity
    */
  def getUserTopChallenges(userId: Long, projectIds: String, challengeIds: String, countryCodes: String,
                           monthDuration: String, start: String, end: String,
                           onlyEnabled: Boolean, limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(this.dataManager.getUserTopChallenges(
        userId, Utils.toLongList(projectIds), Utils.toLongList(projectIds), Utils.toStringList(countryCodes),
        Try(monthDuration.toInt).toOption, Utils.getDate(start), Utils.getDate(end), onlyEnabled, limit, offset
      )))
    }
  }

  def getChallengeSummary(id: Long, survey: Int, priority: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      if (survey == 1) {
        Ok(Json.toJson(
          this.dataManager.getSurveySummary(id, this.getPriority(priority))
        ))
      } else {
        Ok(Json.toJson(
          this.dataManager.getChallengeSummary(challengeId = Some(id), priority = this.getPriority(priority))
        ))
      }
    }
  }

  def getProjectSummary(projects: String, onlyEnabled: Boolean = true): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      Ok(Json.toJson(
        this.dataManager.getChallengeSummary(Utils.toLongList(projects), onlyEnabled = onlyEnabled)
      ))
    }
  }

  def getChallengeActivity(challengeId: Long, start: String, end: String, survey: Int, priority: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      if (survey == 1) {
        Ok(Json.toJson(
          this.dataManager.getSurveyActivity(challengeId, Utils.getDate(start), Utils.getDate(end), this.getPriority(priority))
        ))
      } else {
        Ok(Json.toJson(
          this.dataManager.getChallengeActivity(None, Some(challengeId), Utils.getDate(start), Utils.getDate(end), this.getPriority(priority))
        ))
      }
    }
  }

  def getProjectActivity(projects: String, start: String, end: String): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(
        this.dataManager.getChallengeActivity(Utils.toLongList(projects), None, Utils.getDate(start), Utils.getDate(end))
      ))
    }
  }

  /**
    * Special API for handling data table API requests for challenge summary table
    *
    * @param projectIds A comma separated list of projects to filter by
    * @return
    *
    * @deprecated("This method does not support virtual projects.", "05-23-2019")
    */
  def getChallengeSummaries(projectIds: String, priority: Int, onlyEnabled:Boolean = true): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.userAwareRequest { implicit user =>
      val postData = request.body.asInstanceOf[AnyContentAsFormUrlEncoded].data
      val draw = postData.get("draw").head.head.toInt
      val start = postData.get("start").head.head.toInt
      val length = postData.get("length").head.head.toInt
      val search = postData.get("search[value]").head.head
      val orderDirection = postData.get("order[0][dir]").head.head.toUpperCase
      val orderColumnID = postData.get("order[0][column]").head.head.toInt
      val orderColumnName = postData.get(s"columns[$orderColumnID][name]").head.headOption
      val projectList = Utils.toLongList(projectIds)
      val challengeSummaries =
        this.dataManager.getChallengeSummary(projectList, None, length, start, orderColumnName, orderDirection, search, this.getPriority(priority), onlyEnabled)

      val summaryMap = challengeSummaries.map(summary => Map(
        "id" -> summary.id.toString,
        "name" -> summary.name,
        "complete_percentage" -> summary.actions.percentComplete.toString,
        "available" -> summary.actions.trueAvailable.toString,
        "available_perc" -> summary.actions.percentage(summary.actions.available).toString,
        "fixed" -> summary.actions.fixed.toString,
        "fixed_perc" -> summary.actions.percentage(summary.actions.fixed).toString,
        "false_positive" -> summary.actions.falsePositive.toString,
        "false_positive_perc" -> summary.actions.percentage(summary.actions.falsePositive).toString,
        "skipped" -> summary.actions.skipped.toString,
        "skipped_perc" -> summary.actions.percentage(summary.actions.skipped).toString,
        "already_fixed" -> summary.actions.alreadyFixed.toString,
        "already_fixed_perc" -> summary.actions.percentage(summary.actions.alreadyFixed).toString,
        "too_hard" -> summary.actions.tooHard.toString,
        "too_hard_perc" -> summary.actions.percentage(summary.actions.tooHard).toString
      ))
      Ok(Json.obj(
        "draw" -> JsNumber(draw),
        "recordsTotal" -> JsNumber(dataManager.getTotalSummaryCount(projectList, None)),
        "recordsFiltered" -> JsNumber(dataManager.getTotalSummaryCount(projectList, None, search)),
        "data" -> Json.toJson(summaryMap)
      ))
    }
  }

  private def getPriority(priority: Int): Option[Int] = {
    priority match {
      case x if x >= Challenge.PRIORITY_HIGH & x <= Challenge.PRIORITY_LOW => Some(x)
      case _ => None
    }
  }

  def getRawActivity(userIds: String, projectIds: String, challengeIds: String,
                     start: String, end: String): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(
        dataManager.getRawActivity(Utils.toLongList(userIds), Utils.toLongList(projectIds),
          Utils.toLongList(challengeIds), Utils.getDate(start), Utils.getDate(end))
      ))
    }
  }

  def getStatusActivity(userIds: String, projectIds: String, challengeIds: String,
                        start: String, end: String, newStatus: String, oldStatus: String,
                        limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val users = if (user.isSuperUser) {
        Utils.toLongList(userIds).getOrElse(List.empty)
      } else {
        List(user.id)
      }
      val statusActionLimits = StatusActionLimits(
        Utils.getDate(start),
        Utils.getDate(end),
        users,
        Utils.toLongList(projectIds).getOrElse(List.empty),
        Utils.toLongList(challengeIds).getOrElse(List.empty),
        List.empty,
        Utils.toIntList(newStatus).getOrElse(List.empty),
        Utils.toIntList(oldStatus).getOrElse(List.empty)
      )
      Ok(Json.toJson(
        statusActionManager.getStatusUpdates(user, statusActionLimits, limit, offset)
      ))
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
  def getLatestChallengeActivity(projectIds: String, challengeIds: String,
                                 entries: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      Ok(Json.toJson(this.dataManager.getLatestChallengeActivity(
        Utils.toLongList(projectIds), Utils.toLongList(challengeIds), entries
      )))
    }
  }

  def getStatusSummary(userIds: String, projectIds: String, challengeIds: String, start: String, end: String,
                       limit: Int, offset: Int): Action[AnyContent] = Action.async { implicit request =>
    this.sessionManager.authenticatedRequest { implicit user =>
      val users = if (user.isSuperUser) {
        Utils.toLongList(userIds).getOrElse(List.empty)
      } else {
        List(user.id)
      }
      val statusActionLimits = StatusActionLimits(
        Utils.getDate(start),
        Utils.getDate(end),
        users,
        Utils.toLongList(projectIds).getOrElse(List.empty),
        Utils.toLongList(challengeIds).getOrElse(List.empty),
        List.empty,
        List.empty,
        List.empty
      )
      Ok(Json.toJson(
        statusActionManager.getStatusSummary(user, statusActionLimits, limit, offset)
      ))
    }
  }
}
