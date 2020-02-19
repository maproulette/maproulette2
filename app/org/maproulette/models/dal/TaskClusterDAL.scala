// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm.SqlParser.{get, int, long, str}
import anorm.{SQL, SqlParser, ~}
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.data.Actions
import org.maproulette.exception.InvalidException
import org.maproulette.models.dal.mixin.SearchParametersMixin
import org.maproulette.models.utils.{DALHelper, TransactionManager}
import org.maproulette.models.{ClusteredPoint, Point, PointReview, TaskCluster}
import org.maproulette.session.{SearchParameters, User}
import play.api.db.Database
import play.api.libs.json.{JsString, Json}

/**
  * @author mcuthbert
  */
@Singleton
class TaskClusterDAL @Inject() (override val db: Database, challengeDAL: ChallengeDAL)
    extends DALHelper
    with TransactionManager
    with SearchParametersMixin {

  /**
    * Retrieves task clusters
    *
    * @param params         SearchParameters used to filter the tasks in the cluster
    * @param numberOfPoints Number of cluster points to group all the tasks by
    * @param c              an implicit connection
    * @return A list of task clusters
    */
  def getTaskClusters(
      params: SearchParameters,
      numberOfPoints: Int = TaskDAL.DEFAULT_NUMBER_OF_POINTS
  )(implicit c: Option[Connection] = None): List[TaskCluster] = {
    this.withMRConnection { implicit c =>
      val whereClause = new StringBuilder(" c.deleted = false AND p.deleted = false  ")
      val joinClause = new StringBuilder(
        """
          INNER JOIN challenges c ON c.id = tasks.parent_id
          INNER JOIN projects p ON p.id = c.parent_id
          LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
        """
      )
      val parameters = this.updateWhereClause(params, whereClause, joinClause)(false)
      val where = if (whereClause.isEmpty) {
        whereClause.toString
      } else {
        "WHERE " + whereClause.toString
      }

      val query  = getTaskClusterQuery(joinClause.toString, where, numberOfPoints)
      val result = sqlWithParameters(query, parameters).as(getTaskClusterParser(params).*)
      // Filter out invalid clusters.
      result.filter(_ != None).asInstanceOf[List[TaskCluster]]
    }
  }

  def getTaskClusterParser(params: SearchParameters): anorm.RowParser[Serializable] = {
    int("kmeans") ~ int("numberOfPoints") ~ get[Option[Long]]("taskId") ~
      get[Option[Int]]("taskStatus") ~ get[Option[Int]]("taskPriority") ~ get[Option[String]](
      "geojson"
    ) ~ str("geom") ~
      str("bounding") ~ get[List[Long]]("challengeIds") map {
      case kmeans ~ totalPoints ~ taskId ~ taskStatus ~ taskPriority ~ geojson ~ geom ~ bounding ~ challengeIds =>
        val locationJSON = Json.parse(geom)
        val coordinates  = (locationJSON \ "coordinates").as[List[Double]]
        // Let's check to make sure we received valid number of coordinates.
        if (coordinates.length > 1) {
          val point = Point(coordinates(1), coordinates.head)
          TaskCluster(
            kmeans,
            totalPoints,
            taskId,
            taskStatus,
            taskPriority,
            params,
            point,
            Json.parse(bounding),
            challengeIds,
            geojson.map(Json.parse(_))
          )
        } else {
          None
        }
    }
  }

  def getTaskClusterQuery(joinClause: String, whereClause: String, numberOfPoints: Int): String = {
    s"""SELECT kmeans, count(*) as numberOfPoints,
          CASE WHEN count(*)=1 THEN (array_agg(taskId))[1] END as taskId,
          CASE WHEN count(*)=1 THEN (array_agg(geojson))[1] END as geojson,
          CASE WHEN count(*)=1 THEN (array_agg(taskStatus))[1] END as taskStatus,
          CASE WHEN count(*)=1 THEN (array_agg(taskPriority))[1] END as taskPriority,
          ST_AsGeoJSON(ST_Centroid(ST_Collect(location))) AS geom,
          ST_AsGeoJSON(ST_ConvexHull(ST_Collect(location))) AS bounding,
          array_agg(distinct challengeIds) as challengeIds
       FROM (
         SELECT tasks.id as taskId, tasks.status as taskStatus, tasks.priority as taskPriority, tasks.geojson::TEXT as geojson, ST_ClusterKMeans(tasks.location,
                    (SELECT
                        CASE WHEN COUNT(*) < $numberOfPoints THEN COUNT(*) ELSE $numberOfPoints END
                      FROM tasks
                      ${joinClause}
                      $whereClause
                    )::Integer
                  ) OVER () AS kmeans, tasks.location, tasks.parent_id as challengeIds
         FROM tasks
         ${joinClause}
         $whereClause
       ) AS ksub
       WHERE location IS NOT NULL
       GROUP BY kmeans
       ORDER BY kmeans
    """
  }

  /**
    * Gets the specific tasks within a cluster
    *
    * @param clusterId      The id of the cluster
    * @param params         SearchParameters used to filter the tasks in the cluster
    * @param numberOfPoints Number of cluster points to group all the tasks by
    * @param c              an implicit connection
    * @return A list of clustered task points
    */
  def getTasksInCluster(
      clusterId: Int,
      params: SearchParameters,
      numberOfPoints: Int = TaskDAL.DEFAULT_NUMBER_OF_POINTS
  )(implicit c: Option[Connection] = None): List[ClusteredPoint] = {
    this.withMRConnection { implicit c =>
      val whereClause = new StringBuilder
      val joinClause = new StringBuilder(
        """
              INNER JOIN challenges c ON c.id = tasks.parent_id
              INNER JOIN projects p ON p.id = c.parent_id
              LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
            """
      )
      this.updateWhereClause(params, whereClause, joinClause)(false)
      val where = if (whereClause.isEmpty) {
        whereClause.toString
      } else {
        "WHERE " + whereClause.toString
      }

      val query =
        s"""SELECT *, suggestedfix_geojson::TEXT as suggested_fix,
                     ST_AsGeoJSON(tasks.location) AS location, tasks.priority
                     FROM (
                      SELECT ST_ClusterKMeans(tasks.location,
                        (SELECT
                            CASE WHEN COUNT(*) < $numberOfPoints THEN COUNT(*) ELSE $numberOfPoints END
                          FROM tasks
                          ${joinClause.toString}
                          $where
                        )::Integer
                      ) OVER () as kmeans, tasks.*, task_review.*, c.name
            FROM tasks
            ${joinClause.toString}
            $where
            ) AS t WHERE tasks.kmeans = $clusterId
         """
      SQL(query).as(this.challengeDAL.pointParser.*)
    }
  }

  /**
    * This function will retrieve all the tasks in a given bounded area. You can use various search
    * parameters to limit the tasks retrieved in the bounding box area.
    *
    * @param params        The search parameters from the cookie or the query string parameters.
    * @param limit         A limit for the number of returned tasks
    * @param offset        This allows paging for the tasks within in the bounding box
    * @param excludeLocked Whether to include locked tasks (by other users) or not
    * @param c             An available connection
    * @return The list of Tasks found within the bounding box and the total count of tasks if not bounding
    */
  def getTasksInBoundingBox(
      user: User,
      params: SearchParameters,
      limit: Int = Config.DEFAULT_LIST_SIZE,
      offset: Int = 0,
      excludeLocked: Boolean = false,
      sort: String = "",
      order: String = "ASC"
  )(implicit c: Option[Connection] = None): (Int, List[ClusteredPoint]) = {
    params.location match {
      case Some(sl) => // params has location
      case None =>
        params.boundingGeometries match {
          case Some(bp) => // params has bounding polygons
          case None =>
            throw new InvalidException(
              "Bounding Box (or Bounding Polygons) required to retrieve tasks within a bounding box"
            )
        }
    }

    withMRConnection { implicit c =>
      val whereClause = new StringBuilder(" WHERE p.deleted = false AND c.deleted = false")
      val joinClause = new StringBuilder(
        """
          INNER JOIN challenges c ON c.id = tasks.parent_id
          INNER JOIN projects p ON p.id = c.parent_id
          LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
        """
      )

      if (!excludeLocked) {
        joinClause ++= " LEFT JOIN locked l ON l.item_id = tasks.id "
        this.appendInWhereClause(whereClause, s"(l.id IS NULL OR l.user_id = ${user.id})")
      }

      var sortClause = "ORDER BY RANDOM()"
      if (sort != "") {
        sort match {
          case "reviewRequestedBy" =>
            sortClause = this.order(Some("review_requested_by"), order, "task_review")
          case "reviewedBy" =>
            sortClause = this.order(Some("reviewed_by"), order, "task_review")
          case "reviewStatus" =>
            sortClause = this.order(Some("review_status"), order, "task_review")
          case "reviewedAt" =>
            sortClause = this.order(Some("reviewed_at"), order, "task_review")
          case "mappedOn" =>
            sortClause = this.order(Some("mapped_on"), order, "tasks")
          case _ =>
            sortClause = this.order(Some(sort), order, "tasks")
        }
      }

      // Lets do a total count of tasks we would return if not paging.
      val parameters = this.updateWhereClause(params, whereClause, joinClause)
      this.appendInWhereClause(
        whereClause,
        this.enabled(params.projectEnabled.getOrElse(false), "p")(None)
      )

      val countQuery =
        s"""
          SELECT count(*) FROM tasks
          ${joinClause.toString()}
          ${whereClause.toString()}
        """
      val count = sqlWithParameters(countQuery, parameters).as(SqlParser.int("count").single)

      val query =
        s"""
          SELECT tasks.id, tasks.name, tasks.parent_id, c.name, tasks.instruction, tasks.status, tasks.mapped_on,
                 tasks.bundle_id, tasks.is_bundle_primary, tasks.suggestedfix_geojson::TEXT as suggested_fix,
                 task_review.review_status, task_review.review_requested_by, task_review.reviewed_by, task_review.reviewed_at,
                 task_review.review_started_at,
                 ST_AsGeoJSON(tasks.location) AS location, priority FROM tasks
          ${joinClause.toString()}
          ${whereClause.toString()}
          ${sortClause}
          LIMIT ${sqlLimit(limit)} OFFSET $offset
        """

      val pointParser = long("tasks.id") ~ str("tasks.name") ~ int("tasks.parent_id") ~ str(
        "challenges.name"
      ) ~
        str("tasks.instruction") ~ str("location") ~ int("tasks.status") ~ get[Option[String]](
        "suggested_fix"
      ) ~
        get[Option[DateTime]]("tasks.mapped_on") ~ get[Option[Int]]("task_review.review_status") ~
        get[Option[Long]]("task_review.review_requested_by") ~ get[Option[Long]](
        "task_review.reviewed_by"
      ) ~
        get[Option[DateTime]]("task_review.reviewed_at") ~ get[Option[DateTime]](
        "task_review.review_started_at"
      ) ~
        int("tasks.priority") ~ get[Option[Long]]("tasks.bundle_id") ~
        get[Option[Boolean]]("tasks.is_bundle_primary") map {
        case id ~ name ~ parentId ~ parentName ~ instruction ~ location ~ status ~ suggestedFix ~ mappedOn ~
              reviewStatus ~ reviewRequestedBy ~ reviewedBy ~ reviewedAt ~ reviewStartedAt ~ priority ~
              bundleId ~ isBundlePrimary =>
          val locationJSON = Json.parse(location)
          val coordinates  = (locationJSON \ "coordinates").as[List[Double]]
          val point        = Point(coordinates(1), coordinates.head)
          val pointReview =
            PointReview(reviewStatus, reviewRequestedBy, reviewedBy, reviewedAt, reviewStartedAt)
          ClusteredPoint(
            id,
            -1,
            "",
            name,
            parentId,
            parentName,
            point,
            JsString(""),
            instruction,
            DateTime.now(),
            -1,
            Actions.ITEM_TYPE_TASK,
            status,
            suggestedFix,
            mappedOn,
            pointReview,
            priority,
            bundleId,
            isBundlePrimary
          )
      }

      val results = sqlWithParameters(query, parameters)
      (count, results.as(pointParser.*))
    }
  }
}
