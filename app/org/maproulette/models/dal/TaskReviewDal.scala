// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Provider, Singleton}
import org.joda.time.{DateTime, DateTimeZone}
import org.maproulette.exception.InvalidException
import org.maproulette.models._
import org.maproulette.data._
import org.maproulette.Config
import org.maproulette.permissions.Permission
import org.maproulette.session.{User, SearchParameters}
import org.maproulette.session.dal.UserDAL
import org.maproulette.provider.websockets.WebSocketProvider
import org.maproulette.provider.websockets.WebSocketMessages
import play.api.db.Database
import play.api.libs.ws.WSClient
import play.api.libs.json._

import scala.collection.mutable.ListBuffer

/**
  * @author krotstan
  */
@Singleton
class TaskReviewDAL @Inject()(override val db: Database,
                                override val tagDAL: TagDAL, config: Config,
                                override val permission: Permission,
                                userDAL: Provider[UserDAL],
                                projectDAL: Provider[ProjectDAL],
                                challengeDAL: Provider[ChallengeDAL],
                                notificationDAL: Provider[NotificationDAL],
                                actions: ActionManager,
                                statusActions: StatusActionManager,
                                webSocketProvider: WebSocketProvider,
                                ws: WSClient)
  extends TaskDAL(db, tagDAL, config, permission, userDAL, projectDAL, challengeDAL, notificationDAL,
                  actions, statusActions, webSocketProvider, ws) {

  /**
    * Gets and claims a task for review.
    *
    * @param user The user executing the request
    * @param id id of task that you wish to start/claim
    * @return task
    */
  def startTaskReview(user:User, task:Task) (implicit c:Connection=null) : Option[Task] = {
    if (task.reviewClaimedBy.getOrElse(null) != null &&
        task.reviewClaimedBy.getOrElse(null) != user.id.toLong) {
      throw new InvalidException("This task is already being reviewed by someone else.")
    }

    this.withMRTransaction { implicit c =>
      // Unclaim everything before starting a new task.
      SQL"""UPDATE task_review SET review_claimed_by = NULL, review_claimed_at = NULL
              WHERE review_claimed_by = #${user.id}""".executeUpdate()

      val updatedRows =
        SQL"""UPDATE task_review SET review_claimed_by = #${user.id}, review_claimed_at = NOW()
                WHERE task_id = #${task.id} AND review_claimed_at IS NULL""".executeUpdate()

      // if returning 0, then this is because the item is locked by a different user
      if (updatedRows == 0) {
        throw new IllegalAccessException(s"Current task [${task.id} is locked by another user, cannot start review at this time.")
      }

      try {
        this.unlockItem(user, task)
      } catch {
        case e: Exception => logger.warn(e.getMessage)
      }

      webSocketProvider.sendMessage(WebSocketMessages.reviewClaimed(
        WebSocketMessages.ReviewData(this.getTaskWithReview(task.id))
      ))
    }

    val updatedTask = task.copy(reviewClaimedBy = Option(user.id.toInt))
    this.cacheManager.withOptionCaching { () => Some(updatedTask) }
    Option(updatedTask)
  }

  /**
    * Releases a claim on a task for review.
    *
    * @param user The user executing the request
    * @param id id of task that you wish to release
    * @return task
    */
  def cancelTaskReview(user:User, task:Task) (implicit c:Connection=null) : Option[Task] = {
    if (task.reviewClaimedBy.getOrElse(null) != user.id.toLong) {
      throw new InvalidException("This task is not currently being reviewed by you.")
    }

    this.withMRTransaction { implicit c =>
      val updatedRows = SQL"""UPDATE task_review t SET review_claimed_by = NULL, review_claimed_at = NULL
              WHERE t.task_id = #${task.id}""".executeUpdate()

      // if returning 0, then this is because the item is locked by a different user
      if (updatedRows == 0) {
        throw new IllegalAccessException(s"Current task [${task.id} is locked by another user, cannot cancel review at this time.")
      }

      webSocketProvider.sendMessage(WebSocketMessages.reviewUpdate(
        WebSocketMessages.ReviewData(this.getTaskWithReview(task.id))
      ))
    }

    try {
      this.unlockItem(user, task)
    } catch {
      case e: Exception => logger.warn(e.getMessage)
    }

    val updatedTask = task.copy(reviewClaimedBy = None)
    this.cacheManager.withOptionCaching { () => Some(updatedTask) }
    Option(updatedTask)
  }

  /**
    * Gets and claims the next task for review.
    *
    * @param user The user executing the request
    * @return task
    */
  def nextTaskReview(user:User, searchParameters: SearchParameters, onlySaved: Boolean=false,
                    sort:String, order:String) (implicit c:Connection=null) : Option[Task] = {
    val (count, result) = this.getReviewRequestedTasks(user, searchParameters, null, null, onlySaved, 1, 0, sort, order, false)
    if (count == 0) {
      return None
    }
    else {
      return Some(result.head)
    }
  }

  /**
    * Gets a list of tasks that have requested review (and are in this user's project group)
    *
    * @param user The user executing the request
    * @param startDate Limit tasks to reviewed after date (YYYY-MM-DD)
    * @param endDate Limit tasks to reviewed before date (YYYY-MM-DD)
    * @param limit The number of tasks to return
    * @param offset Offset to start paging
    * @param sort Sort column
    * @param order DESC or ASC
    * @param includeDisputed Whether disputed tasks whould be returned in results (default is true)
    * @return A list of tasks
    */
  def getReviewRequestedTasks(user:User, searchParameters: SearchParameters,
                              startDate:String, endDate:String, onlySaved: Boolean=false,
                              limit:Int = -1, offset:Int=0, sort:String, order:String, includeDisputed: Boolean = true)
                    (implicit c:Connection=null) : (Int, List[Task]) = {
    var orderByClause = ""
    val whereClause = includeDisputed match {
      case true =>
        new StringBuilder(s"(task_review.review_status=${Task.REVIEW_STATUS_REQUESTED} OR task_review.review_status=${Task.REVIEW_STATUS_DISPUTED})")
      case false =>
        new StringBuilder(s"task_review.review_status=${Task.REVIEW_STATUS_REQUESTED}")
    }

    val joinClause = new StringBuilder("INNER JOIN challenges c ON c.id = tasks.parent_id ")
    joinClause ++= "LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id "
    joinClause ++= "INNER JOIN projects p ON p.id = c.parent_id "

    if (onlySaved) {
      joinClause ++= "INNER JOIN saved_challenges sc ON sc.challenge_id = c.id "
      this.appendInWhereClause(whereClause, s"sc.user_id = ${user.id}")
    }

    this.appendInWhereClause(whereClause,
      s"(task_review.review_claimed_at IS NULL OR task_review.review_claimed_by = ${user.id})")

    val parameters = new ListBuffer[NamedParameter]()
    parameters ++= addSearchToQuery(searchParameters, whereClause)

    setupReviewSearchClause(whereClause, joinClause, searchParameters, startDate, endDate)

    sort match {
      case s if s.nonEmpty =>
        orderByClause = this.order(Some(s), order, "", false)
      case _ => // ignore
    }

    val query = user.isSuperUser match {
      case true =>
        s"""
          SELECT tasks.${this.retrieveColumnsWithReview} FROM tasks
          ${joinClause}
          WHERE
          ${whereClause}
          ${orderByClause}
          LIMIT ${sqlLimit(limit)} OFFSET ${offset}
         """
      case default =>
        if (user.settings.isReviewer.getOrElse(false)) {
          // You see review task when:
          // 1. Project and Challenge enabled (so visible to everyone)
          // 2. You own the Project
          // 3. You manage the project (your user group matches groups of project)
          s"""
            SELECT tasks.${this.retrieveColumnsWithReview} FROM tasks
            ${joinClause}
            INNER JOIN groups g ON g.project_id = p.id
            INNER JOIN user_groups ug ON g.id = ug.group_id
            WHERE ((p.enabled AND c.enabled) OR
                    p.owner_id = ${user.osmProfile.id} OR
                    ug.osm_user_id = ${user.osmProfile.id}) AND
                    task_review.review_requested_by != ${user.id} AND
            ${whereClause}
            ${orderByClause}
            LIMIT ${sqlLimit(limit)} OFFSET ${offset}
           """
         }
         else {
           return (0, List[Task]())
         }
    }

    val countQuery = user.isSuperUser match {
      case true =>
        s"""
          SELECT count(*) FROM tasks
          ${joinClause}
          WHERE ${whereClause}
        """
      case default =>
        s"""
          SELECT count(*) FROM tasks
          ${joinClause}
          INNER JOIN groups g ON g.project_id = p.id
          INNER JOIN user_groups ug ON g.id = ug.group_id
          WHERE ((p.enabled AND c.enabled) OR
                  p.owner_id = ${user.osmProfile.id} OR
                  ug.osm_user_id = ${user.osmProfile.id}) AND
                  task_review.review_requested_by != ${user.id} AND
          ${whereClause}
        """
    }

    var count = 0
    val tasks = this.cacheManager.withIDListCaching { implicit cachedItems =>
      this.withMRTransaction { implicit c =>
        count = sqlWithParameters(countQuery, parameters).as(SqlParser.int("count").single)
        sqlWithParameters(query, parameters).as(this.parser.*)
      }
    }

    return (count, tasks)
  }

  /**
    * Gets a list of tasks that have been reviewed (either by this user or requested by this user)
    *
    * @param user The user executing the request
    * @param startDate Limit tasks to reviewed after date (YYYY-MM-DD)
    * @param endDate Limit tasks to reviewed before date (YYYY-MM-DD)
    * @param asReviewer Whether we should return tasks reviewed by this user or reqested by this user
    * @param allowReviewNeeded Whether we should include review requested tasks as well
    * @param limit The amount of tasks to be returned
    * @param offset Offset to start paging
    * @param sort Column to sort
    * @param order DESC or ASC
    * @return A list of tasks
    */
  def getReviewedTasks(user:User, searchParameters: SearchParameters,
                       mappers:Option[List[String]]=None, reviewers:Option[List[String]]=None,
                       startDate:String, endDate:String, allowReviewNeeded:Boolean=false,
                       limit:Int = -1, offset:Int=0, sort:String, order:String)
                       (implicit c:Connection=null) : (Int, List[Task]) = {
    var orderByClause = ""
    val whereClause = new StringBuilder()
    val joinClause = new StringBuilder("INNER JOIN challenges c ON c.id = tasks.parent_id ")
    joinClause ++= "LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id "
    joinClause ++= "INNER JOIN projects p ON p.id = c.parent_id "

    mappers match {
      case Some(m) =>
        if (m.size > 0) {
          whereClause ++= s"task_review.review_requested_by IN (${m.mkString(",")}) "
        }
        else {
          whereClause ++= s"task_review.review_requested_by IS NOT NULL "
        }
      case _ => whereClause ++= s"task_review.review_requested_by IS NOT NULL "
    }

    reviewers match {
      case Some(r) =>
        if (r.size > 0) {
          whereClause ++= s" AND task_review.reviewed_by IN (${r.mkString(",")})"
        }
      case _ => // do nothing
    }

    val parameters = new ListBuffer[NamedParameter]()
    parameters ++= addSearchToQuery(searchParameters, whereClause)

    if (!allowReviewNeeded) {
      this.appendInWhereClause(whereClause, s"task_review.review_status <> ${Task.REVIEW_STATUS_REQUESTED} ")
    }

    setupReviewSearchClause(whereClause, joinClause, searchParameters, startDate, endDate)

    sort match {
      case s if s.nonEmpty =>
        orderByClause = this.order(Some(s), order, "", false)
      case _ => // ignore
    }

    val query = user.isSuperUser match {
      case true =>
        s"""
          SELECT tasks.${this.retrieveColumnsWithReview} FROM tasks
          ${joinClause}
          WHERE
          ${whereClause}
          ${orderByClause}
          LIMIT ${sqlLimit(limit)} OFFSET ${offset}
         """
      case default =>
        if (user.settings.isReviewer.getOrElse(false)) {
          // You see review task when:
          // 1. Project and Challenge enabled (so visible to everyone)
          // 2. You own the Project
          // 3. You manage the project (your user group matches groups of project)
          // 4. You asked for the review or you performed the review
          s"""
            SELECT tasks.${this.retrieveColumnsWithReview} FROM tasks
            ${joinClause}
            INNER JOIN groups g ON g.project_id = p.id
            INNER JOIN user_groups ug ON g.id = ug.group_id
            WHERE ((p.enabled AND c.enabled) OR
                    p.owner_id = ${user.osmProfile.id} OR
                    ug.osm_user_id = ${user.osmProfile.id} OR
                    task_review.review_requested_by = ${user.id} OR
                    task_review.reviewed_by = ${user.id}) AND
            ${whereClause}
            ${orderByClause}
            LIMIT ${sqlLimit(limit)} OFFSET ${offset}
           """
         }
         else {
           return (0, List[Task]())
         }
    }

    val countQuery = user.isSuperUser match {
      case true =>
        s"""
          SELECT count(*) FROM tasks
          ${joinClause}
          WHERE ${whereClause}
        """
      case default =>
        s"""
          SELECT count(*) FROM tasks
          ${joinClause}
          INNER JOIN groups g ON g.project_id = p.id
          INNER JOIN user_groups ug ON g.id = ug.group_id
          WHERE ((p.enabled AND c.enabled) OR
                  p.owner_id = ${user.osmProfile.id} OR
                  ug.osm_user_id = ${user.osmProfile.id} OR
                  task_review.review_requested_by = ${user.id} OR
                  task_review.reviewed_by = ${user.id}) AND
          ${whereClause}
        """
    }

   var count = 0
   val tasks = this.cacheManager.withIDListCaching { implicit cachedItems =>
      this.withMRTransaction { implicit c =>
        count = sqlWithParameters(countQuery, parameters).as(SqlParser.int("count").single)
        sqlWithParameters(query, parameters).as(this.parser.*)
      }
    }

    return (count, tasks)
  }


  /**
    * Gets a list of tasks that have been reviewed (either by this user or requested by this user)
    *
    * @param user The user executing the request
    * @param reviewTasksType
    * @param searchParameters
    * @param startDate Limit tasks to reviewed after date (YYYY-MM-DD)
    * @param endDate Limit tasks to reviewed before date (YYYY-MM-DD)
    * @return A list of tasks
    */
  def getReviewMetrics(user:User, reviewTasksType:Int, searchParameters: SearchParameters,
                       mappers:Option[List[String]]=None, reviewers:Option[List[String]]=None,
                       startDate:String, endDate:String, onlySaved: Boolean=false)
                       (implicit c:Connection=null) : List[ReviewMetrics] = {

   // 1: REVIEW_TASKS_TO_BE_REVIEWED = 'tasksToBeReviewed'
   // 2: MY_REVIEWED_TASKS = 'myReviewedTasks'
   // 3: REVIEW_TASKS_BY_ME = 'tasksReviewedByMe'
   // 4: ALL_REVIEWED_TASKS = 'allReviewedTasks'

    val joinClause = new StringBuilder("INNER JOIN challenges c ON c.id = tasks.parent_id ")
    joinClause ++= "LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id "
    joinClause ++= "INNER JOIN projects p ON p.id = c.parent_id "

    var whereClause = new StringBuilder()

    mappers match {
      case Some(m) =>
        if (m.size > 0) {
          whereClause ++= s"task_review.review_requested_by IN (${m.mkString(",")}) "
        }
        else {
          whereClause ++= s"task_review.review_requested_by IS NOT NULL "
        }
      case _ => whereClause ++= s"task_review.review_requested_by IS NOT NULL "
    }

    reviewers match {
      case Some(r) =>
        if (r.size > 0) {
          whereClause ++= s" AND task_review.reviewed_by IN (${r.mkString(",")}) "
        }
      case _ => // do nothing
    }

    if (reviewTasksType == 1) {
      whereClause = new StringBuilder(s"(task_review.review_status=${Task.REVIEW_STATUS_REQUESTED} OR task_review.review_status=${Task.REVIEW_STATUS_DISPUTED})")

      if (onlySaved) {
        joinClause ++= "INNER JOIN saved_challenges sc ON sc.challenge_id = c.id "
        this.appendInWhereClause(whereClause, s"sc.user_id = ${user.id} ")
      }

      if (!user.isSuperUser) {
        joinClause ++=
          s""" INNER JOIN groups g ON g.project_id = p.id
               INNER JOIN user_groups ug ON g.id = ug.group_id """

        whereClause ++=
          s""" AND ((p.enabled AND c.enabled) OR
                p.owner_id = ${user.osmProfile.id} OR
                ug.osm_user_id = ${user.osmProfile.id}) AND
                task_review.review_requested_by != ${user.id} """
      }
    }
    else {
      if (!user.isSuperUser) {
        joinClause ++=
          s""" INNER JOIN groups g ON g.project_id = p.id
               INNER JOIN user_groups ug ON g.id = ug.group_id """

        whereClause ++=
          s""" AND ((p.enabled AND c.enabled) OR
                p.owner_id = ${user.osmProfile.id} OR
                ug.osm_user_id = ${user.osmProfile.id} OR
                task_review.review_requested_by = ${user.id} OR
                task_review.reviewed_by = ${user.id})"""
      }
    }

    val parameters = new ListBuffer[NamedParameter]()
    parameters ++= addSearchToQuery(searchParameters, whereClause)

    if (reviewTasksType == 2) {
      this.appendInWhereClause(whereClause, s"task_review.review_status <> ${Task.REVIEW_STATUS_REQUESTED} ")
    }

    setupReviewSearchClause(whereClause, joinClause, searchParameters, startDate, endDate)

    val query = s"""
     SELECT COUNT(*) AS total,
     COUNT(review_status) FILTER (where review_status = 0) AS requested,
     COUNT(review_status) FILTER (where review_status = 1) AS approved,
     COUNT(review_status) FILTER (where review_status = 2) AS rejected,
     COUNT(review_status) FILTER (where review_status = 3) AS assisted,
     COUNT(review_status) FILTER (where review_status = 4) AS disputed,
     COUNT(tasks.status) FILTER (where tasks.status = 1) AS fixed,
     COUNT(tasks.status) FILTER (where tasks.status = 2) AS falsePositive,
     COUNT(tasks.status) FILTER (where tasks.status = 3) AS skipped,
     COUNT(tasks.status) FILTER (where tasks.status = 5) AS alreadyFixed,
     COUNT(tasks.status) FILTER (where tasks.status = 6) AS tooHard
     FROM tasks
     ${joinClause}
     WHERE
     ${whereClause}
    """
    val reviewMetricsParser: RowParser[ReviewMetrics] = {
        get[Int]("total") ~
        get[Int]("requested") ~
        get[Int]("approved") ~
        get[Int]("rejected") ~
        get[Int]("assisted") ~
        get[Int]("disputed") ~
        get[Int]("fixed") ~
        get[Int]("falsePositive") ~
        get[Int]("skipped") ~
        get[Int]("alreadyFixed") ~
        get[Int]("tooHard") map {
        case total ~ requested ~ approved ~ rejected ~ assisted ~ disputed ~
             fixed ~ falsePositive ~ skipped ~ alreadyFixed ~ tooHard => {
          new ReviewMetrics(total, requested, approved, rejected, assisted, disputed,
                            fixed, falsePositive, skipped, alreadyFixed, tooHard)
        }
      }
    }

    this.withMRTransaction { implicit c =>
      sqlWithParameters(query, parameters).as(reviewMetricsParser.*)
    }
  }

  /**
    * Retrieves task clusters for review criteria
    *
    * @param user
    * @param reviewTasksType
    * @param params         SearchParameters used to filter the tasks in the cluster
    * @param numberOfPoints Number of cluster points to group all the tasks by
    * @param startDate
    * @param endDate
    * @param c              an implicit connection
    * @return A list of task clusters
    */
  def getReviewTaskClusters(user:User, reviewTasksType:Int, params: SearchParameters,
                            numberOfPoints: Int = TaskDAL.DEFAULT_NUMBER_OF_POINTS,
                            startDate:String, endDate:String, onlySaved: Boolean=false)
                     (implicit c: Option[Connection] = None): List[TaskCluster] = {
    this.withMRConnection { implicit c =>
      val taskClusterParser = int("kmeans") ~ int("numberOfPoints") ~
        str("geom") ~ str("bounding") map {
        case kmeans ~ totalPoints ~ geom ~ bounding =>
          val locationJSON = Json.parse(geom)
          val coordinates = (locationJSON \ "coordinates").as[List[Double]]
          val point = Point(coordinates(1), coordinates.head)
          TaskCluster(kmeans, totalPoints, params, point, Json.parse(bounding))
      }

      val fetchBy = if (reviewTasksType == 2) "task_review.reviewed_by" else "task_review.review_requested_by"
      val joinClause = new StringBuilder("INNER JOIN challenges c ON c.id = tasks.parent_id ")
      joinClause ++= "LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id "
      joinClause ++= "INNER JOIN projects p ON p.id = c.parent_id "

      var whereClause = new StringBuilder(s"${fetchBy}=${user.id}")
      if (reviewTasksType == 1) {
        whereClause = new StringBuilder(s"(task_review.review_status=${Task.REVIEW_STATUS_REQUESTED} OR task_review.review_status=${Task.REVIEW_STATUS_DISPUTED})")

        if (onlySaved) {
          joinClause ++= "INNER JOIN saved_challenges sc ON sc.challenge_id = c.id "
          this.appendInWhereClause(whereClause, s"sc.user_id = ${user.id} ")
        }

        if (!user.isSuperUser) {
          joinClause ++=
            s""" INNER JOIN groups g ON g.project_id = p.id
                 INNER JOIN user_groups ug ON g.id = ug.group_id """

          whereClause ++=
            s""" AND ((p.enabled AND c.enabled) OR
                  p.owner_id = ${user.osmProfile.id} OR
                  ug.osm_user_id = ${user.osmProfile.id}) AND
                  task_review.review_requested_by != ${user.id} """
        }
      }

      val parameters = new ListBuffer[NamedParameter]()
      parameters ++= addSearchToQuery(params, whereClause)

      if (reviewTasksType == 2) {
        this.appendInWhereClause(whereClause, s"task_review.review_status <> ${Task.REVIEW_STATUS_REQUESTED} ")
      }

      setupReviewSearchClause(whereClause, joinClause, params, startDate, endDate)

      val where = if (whereClause.isEmpty) {
        whereClause.toString
      } else {
        "WHERE " + whereClause.toString
      }

      val query =
        s"""SELECT kmeans, count(*) as numberOfPoints,
                ST_AsGeoJSON(ST_Centroid(ST_Collect(location))) AS geom,
                ST_AsGeoJSON(ST_ConvexHull(ST_Collect(location))) AS bounding
             FROM (
               SELECT ST_ClusterKMeans(tasks.location,
                          (SELECT
                              CASE WHEN COUNT(*) < $numberOfPoints THEN COUNT(*) ELSE $numberOfPoints END
                            FROM tasks
                            ${joinClause.toString}
                            $where
                          )::Integer
                        ) OVER () AS kmeans, tasks.location
               FROM tasks
               ${joinClause.toString}
               $where
             ) AS ksub
             WHERE location IS NOT NULL
             GROUP BY kmeans
             ORDER BY kmeans
           """
      sqlWithParameters(query, parameters).as(taskClusterParser.*)
    }
  }


  /**
   * private setup the search clauses for searching the review tables
   */
  private def setupReviewSearchClause(whereClause: StringBuilder, joinClause: StringBuilder,
                                      searchParameters: SearchParameters,
                                      startDate: String, endDate: String) {
    searchParameters.owner match {
     case Some(o) if o.nonEmpty =>
       joinClause ++= "INNER JOIN users u ON u.id = task_review.review_requested_by "
       this.appendInWhereClause(whereClause, s"LOWER(u.name) LIKE LOWER('%${o}%')")
     case _ => // ignore
    }

    searchParameters.reviewer match {
     case Some(r) if r.nonEmpty =>
       joinClause ++= "INNER JOIN users u2 ON u2.id = task_review.reviewed_by "
       this.appendInWhereClause(whereClause, s"LOWER(u2.name) LIKE LOWER('%${r}%')")
     case _ => // ignore
    }

    searchParameters.taskStatus match {
      case Some(statuses) if statuses.nonEmpty =>
        val statusClause = new StringBuilder(s"(tasks.status IN (${statuses.mkString(",")})")
        if (statuses.contains(-1)) {
          statusClause ++= " OR c.status IS NULL"
        }
        statusClause ++= ")"
        this.appendInWhereClause(whereClause, statusClause.toString())
      case Some(statuses) if statuses.isEmpty => //ignore this scenario
      case _ =>
    }

    searchParameters.taskReviewStatus match {
      case Some(statuses) if statuses.nonEmpty =>
        val statusClause = new StringBuilder(s"(task_review.review_status IN (${statuses.mkString(",")}))")
        this.appendInWhereClause(whereClause, statusClause.toString())
      case Some(statuses) if statuses.isEmpty => //ignore this scenario
      case _ =>
    }

    searchParameters.location match {
      case Some(sl) => this.appendInWhereClause(whereClause, s"tasks.location @ ST_MakeEnvelope (${sl.left}, ${sl.bottom}, ${sl.right}, ${sl.top}, 4326)")
      case None => // do nothing
    }

    searchParameters.projectSearch match {
      case Some(ps) => {
        val projectName = ps.replace("'","''")
        this.appendInWhereClause(whereClause, s"""LOWER(p.display_name) LIKE LOWER('%${projectName}%')""")
      }
      case None => // do nothing
    }

    if (startDate != null && startDate.matches("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")) {
      this.appendInWhereClause(whereClause, "reviewed_at >= '" + startDate + " 00:00:00'")
    }

    if (endDate != null && endDate.matches("[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]")) {
      this.appendInWhereClause(whereClause, "reviewed_at <= '" + endDate + " 23:59:59'")
    }
  }

}
