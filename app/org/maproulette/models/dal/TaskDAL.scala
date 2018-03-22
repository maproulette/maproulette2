// Copyright (C) 2016 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection
import javax.inject.{Inject, Provider, Singleton}

import anorm.SqlParser._
import anorm._
import com.vividsolutions.jts.geom.Envelope
import org.apache.commons.lang3.StringUtils
import org.joda.time.{DateTime, DateTimeZone}
import org.maproulette.Config
import org.maproulette.actions._
import org.maproulette.cache.CacheManager
import org.maproulette.exception.{InvalidException, NotFoundException}
import org.maproulette.models.utils.DALHelper
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.session.dal.UserDAL
import org.maproulette.session.{SearchParameters, User}
import play.api.Logger
import play.api.db.Database
import play.api.libs.json._
import play.api.libs.ws.WSClient
import org.wololo.geojson.{FeatureCollection, GeoJSONFactory}
import org.wololo.jts2geojson.GeoJSONReader

import scala.collection.mutable.ListBuffer
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.xml.XML

/**
  * The data access layer for the Task objects
  *
  * @author cuthbertm
  */
@Singleton
class TaskDAL @Inject()(override val db: Database,
                        override val tagDAL: TagDAL, config: Config,
                        override val permission: Permission,
                        userDAL: Provider[UserDAL],
                        projectDAL: Provider[ProjectDAL],
                        challengeDAL: Provider[ChallengeDAL],
                        actions: ActionManager,
                        statusActions:StatusActionManager,
                        ws:WSClient)
  extends BaseDAL[Long, Task] with DALHelper with TagDALMixin[Task] with Locking[Task] {
  import scala.concurrent.ExecutionContext.Implicits.global

  // The cache manager for that tasks
  override val cacheManager = new CacheManager[Long, Task]()
  // The database table name for the tasks
  override val tableName: String = "tasks"
  // The columns to be retrieved for the task. Reason this is required is because one of the columns
  // "tasks.location" is a PostGIS object in the database and we want it returned in GeoJSON instead
  // so the ST_AsGeoJSON function is used to convert it to geoJSON
  override val retrieveColumns: String = "*, ST_AsGeoJSON(tasks.location) AS location"

  // The anorm row parser to convert records from the task table to task objects
  implicit val parser: RowParser[Task] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[DateTime]("tasks.created") ~
      get[DateTime]("tasks.modified") ~
      get[Long]("parent_id") ~
      get[Option[String]]("tasks.instruction") ~
      get[Option[String]]("location") ~
      get[Option[Int]]("tasks.status") ~
      get[Int]("tasks.priority") ~
      get[Option[Long]]("tasks.changeset_id") map {
      case id ~ name ~ created ~ modified ~ parent_id ~ instruction ~ location ~ status ~ priority ~ changesetId =>
        Task(id, name, created, modified, parent_id, instruction, location, this.getTaskGeometries(id), status, priority, changesetId)
    }
  }

  val commentParser: RowParser[Comment] = {
    get[Long]("task_comments.id") ~
    get[Long]("task_comments.osm_id") ~
    get[String]("users.name") ~
    get[Long]("task_comments.task_id") ~
    get[Long]("task_comments.challenge_id") ~
    get[Long]("task_comments.project_id") ~
    get[DateTime]("task_comments.created") ~
    get[String]("task_comments.comment") ~
    get[Option[Long]]("task_comments.action_id") map {
      case id ~ osm_id ~ osm_name ~ taskId ~ challengeId ~ projectId ~ created ~ comment ~ action_id =>
        Comment(id, osm_id, osm_name, taskId, challengeId, projectId, created, comment, action_id)
    }
  }

  /**
    * This will retrieve the root object in the hierarchy of the object, by default the root
    * object is itself.
    *
    * @param obj Either a id for the challenge, or the challenge itself
    * @param c   The connection if any
    * @return The object that it is retrieving
    */
  override def retrieveRootObject(obj: Either[Long, Task], user: User)(implicit c: Option[Connection] = None): Option[Project] = {
    obj match {
      case Left(id) =>
        this.permission.hasReadAccess(TaskType(), user)(id)
        this.projectDAL.get().cacheManager.withOptionCaching { () =>
          this.withMRConnection { implicit c =>
            SQL"""SELECT p.* FROM projects p
             INNER JOIN challenges c ON c.parent_id = p.id
             INNER JOIN task t ON t.parent_id = c.id
             WHERE t.id = $id
           """.as(this.projectDAL.get().parser.*).headOption
          }
        }
      case Right(task) =>
        this.permission.hasReadAccess(task, user)
        this.projectDAL.get().cacheManager.withOptionCaching { () =>
          this.withMRConnection { implicit c =>
            SQL"""SELECT p.* FROM projects p
             INNER JOIN challenges c ON c.parent_id = p.id
             WHERE c.id = ${task.parent}
           """.as(this.projectDAL.get().parser.*).headOption
          }
        }
    }
  }

  private def getTaskGeometries(id: Long)(implicit c: Option[Connection] = None): String = taskGeometries(id, "task_geometries")

  def getSuggestedFix(id: Long)(implicit c: Option[Connection] = None): String = taskGeometries(id, "task_suggested_fix")

  /**
    * Retrieve all the geometries for the task
    *
    * @param id Id for the task
    * @return A feature collection geojson of all the task geometries
    */
  private def taskGeometries(id: Long, tableName: String)(implicit c: Option[Connection] = None): String = {
    this.withMRConnection { implicit c =>
      SQL"""SELECT row_to_json(fc)::text as geometries
            FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features
                   FROM ( SELECT 'Feature' As type,
                                  ST_AsGeoJSON(lg.geom)::json As geometry,
                                  hstore_to_json(lg.properties) As properties
                          FROM #$tableName As lg
                          WHERE task_id = $id
                    ) As f
            )  As fc""".as(SqlParser.str("geometries").single)
    }
  }

  /**
    * Inserts a new task object into the database
    *
    * @param task The task to be inserted into the database
    * @param user The user executing the task
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(task: Task, user: User)(implicit c: Option[Connection] = None): Task = {
    val newTask = this.mergeUpdate(task, user)(-1) match {
      case Some(t) => t
      case None => throw new Exception("Unknown failure occurred while creating new task.")
    }
    // update the task priority inside a future, so fire and forget and don't impact the performance of the insert
    Future { this.updateTaskPriority(newTask.id, user) }
    newTask
  }

  /**
    * Updates a task object in the database.
    *
    * @param value A json object containing fields to be updated for the task
    * @param user  The user executing the task
    * @param id    The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(value: JsValue, user: User)(implicit id: Long, c: Option[Connection] = None): Option[Task] = {
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      val name = (value \ "name").asOpt[String].getOrElse(cachedItem.name)
      val parentId = (value \ "parentId").asOpt[Long].getOrElse(cachedItem.parent)
      val instruction = (value \ "instruction").asOpt[String].getOrElse(cachedItem.instruction.getOrElse(""))
      val status = (value \ "status").asOpt[Int].getOrElse(cachedItem.status.getOrElse(0))
      if (!Task.isValidStatusProgression(cachedItem.status.getOrElse(0), status)) {
        throw new InvalidException(s"Could not set status for task [$id], " +
          s"progression from ${cachedItem.status.getOrElse(0)} to $status not valid.")
      }
      val priority = (value \ "priority").asOpt[Int].getOrElse(cachedItem.priority)
      val geometries = (value \ "geometries").asOpt[String].getOrElse(cachedItem.geometries)
      val changesetId = (value \ "changesetId").asOpt[Long].getOrElse(cachedItem.changesetId.getOrElse(-1L))

      this.mergeUpdate(cachedItem.copy(name = name,
        parent = parentId,
        instruction = Some(instruction),
        status = Some(status),
        geometries = geometries,
        priority = priority,
        changesetId = Some(changesetId)), user)
    }
  }

  /**
    * This is a merge update function that will update the task if it exists otherwise it will
    * insert a new item.
    *
    * @param element The element that needs to be inserted or updated. Although it could be updated,
    *                it requires the element itself in case it needs to be inserted
    * @param user    The user that is executing the function
    * @param id      The id of the element that is being updated/inserted
    * @param c       A connection to execute against
    * @return
    */
  override def mergeUpdate(element: Task, user: User)(implicit id: Long, c: Option[Connection] = None): Option[Task] = {
    this.permission.hasObjectWriteAccess(element, user)
    // before clearing the cache grab the cachedItem
    // by setting the delete implicit to true we clear out the cache for the element
    // The cachedItem could be
    val cachedItem = this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      Some(cachedItem)
    }(id, true, true)
    val updatedTask:Option[Task] = this.withMRTransaction { implicit c =>
        val query = "SELECT create_update_task({name}, {parentId}, {instruction}, {status}, {id}, {priority}, {changesetId}, {reset})"

        val updatedTaskId = SQL(query).on(
          NamedParameter("name", ParameterValue.toParameterValue(element.name)),
          NamedParameter("parentId", ParameterValue.toParameterValue(element.parent)),
          NamedParameter("instruction", ParameterValue.toParameterValue(element.instruction.getOrElse(""))),
          NamedParameter("status", ParameterValue.toParameterValue(element.status.getOrElse(Task.STATUS_CREATED))),
          NamedParameter("id", ParameterValue.toParameterValue(element.id)),
          NamedParameter("priority", ParameterValue.toParameterValue(element.priority)),
          NamedParameter("changesetId", ParameterValue.toParameterValue(element.changesetId.getOrElse(-1L))),
          NamedParameter("reset", ParameterValue.toParameterValue(config.taskReset + " days"))
        ).as(long("create_update_task").*).head
        if (cachedItem.isEmpty || !StringUtils.equalsIgnoreCase(cachedItem.get.geometries, element.geometries)) {
          c.commit()
          this.updateGeometries(updatedTaskId, Json.parse(element.geometries))
          this.updateTaskLocation(updatedTaskId)
        }
        Some(element.copy(id = updatedTaskId))
      }
    updatedTask match {
      case Some(t) => Future { this.updateTaskPriority(t.id, user) }
      case None => //just ignore and do nothing
    }
    updatedTask
  }

  /**
    * There can only be a single suggested fix for a task, so if you add one it will remove any
    * others that were added previously.
    *
    * @param taskId The id for the task
    * @param value  The JSON value for the suggested fix
    * @param c
    */
  def addSuggestedFix(taskId: Long, value: JsValue)(implicit c: Option[Connection] = None): Unit =
    updateGeometries(taskId, value, false, true, "task_suggested_fix")

  /**
    * Function that updates the geometries for the task, either during an insert or update
    *
    * @param taskId      The task Id to update the geometries for
    * @param value       The geojson that contains the geometries/features
    * @param setLocation Whether to set the location based on the geometries or not
    */
  private def updateGeometries(taskId: Long, value: JsValue, setLocation: Boolean = false, isNew: Boolean = false,
                               tableName: String = "task_geometries")(implicit c: Option[Connection] = None): Unit = {
    this.withMRTransaction { implicit c =>
      if (!isNew) {
        SQL"""DELETE FROM #$tableName WHERE task_id = $taskId""".executeUpdate()
      }
      val features = (value \ "features").as[List[JsValue]]
      if (features.isEmpty) {
        c.rollback()
        throw new InvalidException(s"No features found for task [$taskId].")
      }
      val indexedValues = features.zipWithIndex
      val rows = indexedValues.map {
        case (_, i) => s"({taskId}, ST_SetSRID(ST_GeomFromGeoJSON({geom_$i}),4326), {props_$i}::hstore)"
      }.mkString(",")
      val parameters = indexedValues.flatMap { case (featureJson, i) =>
        val props = (featureJson \ "properties").asOpt[JsObject] match {
          case Some(JsObject(p)) =>
            p.toMap.map(v => {
              val toStr = v._2 match {
                case obj:JsString => obj.value
                case obj => obj.toString
              }
              s""""${v._1}"=>"${toStr.replaceAll("\\\"", "\\\\\"")}""""
            }).mkString(",")
          case None => ""
        }
        Seq(
          NamedParameter(s"geom_$i", ParameterValue.toParameterValue((featureJson \ "geometry").as[JsValue].toString)),
          NamedParameter(s"props_$i", ParameterValue.toParameterValue(props))
        )
      } ++ Seq(NamedParameter("taskId", ParameterValue.toParameterValue(taskId)))
      SQL(s"INSERT INTO $tableName (task_id, geom, properties) VALUES " + rows)
        .on(parameters: _*)
        .execute()
      c.commit()
    }
  }

  def updateTaskLocation(taskId: Long)(implicit c: Option[Connection] = None): Option[Task] = {
    implicit val id = taskId
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit task =>
      this.withMRTransaction { implicit c =>
        // Update the location of the particular task
        SQL"""UPDATE tasks
            SET location = (SELECT ST_Centroid(ST_Collect(ST_Makevalid(geom)))
                            FROM task_geometries WHERE task_id = $taskId)
            WHERE id = $taskId RETURNING #${this.retrieveColumns}
        """.as(this.parser.singleOpt)
      }
    }
  }

  def updateTaskLocations(challengeId: Long)(implicit c: Option[Connection] = None): Int = {
    // clear the cache, because we don't know how many tasks have actually been updated
    this.cacheManager.clearCaches
    this.withMRTransaction { implicit c =>
      // update all the tasks of a particular challenge
      val query = s"""DO $$$$
            DECLARE
              rec RECORD;
            BEGIN
              FOR rec IN SELECT task_id, ST_Centroid(ST_Collect(ST_Makevalid(geom))) AS location
                          FROM task_geometries tg
                          INNER JOIN tasks t on t.id = tg.task_id
                          WHERE t.parent_id = $challengeId
                          GROUP BY task_id LOOP
                UPDATE tasks SET location = rec.location WHERE tasks.id = rec.task_id and parent_id = $challengeId;
              END LOOP;
            END$$$$;
        """
      SQL(query).executeUpdate()
    }
  }

  /**
    * This function will update the tasks priority based on the parent challenge information. It will
    * check first to see if it falls inside the HIGH priority bucket, then MEDIUM then LOW. If it doesn't
    * fall into any priority bucket, it will then set the priority to the default priority defined
    * in the parent challenge
    *
    * @param taskId The id for the task to update the priority for
    * @param c      The database connection
    */
  def updateTaskPriority(taskId: Long, user: User)(implicit c: Option[Connection] = None): Unit = {
    implicit val id = taskId
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit task =>
      this.withMRTransaction { implicit c =>
        implicit val id = taskId
        this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit task =>
          this.permission.hasObjectWriteAccess(task, user)
          // get the parent challenge, as we need the priority information
          val parentChallenge = this.challengeDAL.get().retrieveById(task.parent) match {
            case Some(c) => c
            case None => throw new NotFoundException(s"No parent was found for task [$taskId], this should never happen.")
          }
          val newPriority = task.getTaskPriority(parentChallenge)
          this.withMRTransaction { implicit c =>
            // Update the location of the particular task
            SQL"""UPDATE tasks
              SET priority = $newPriority
              WHERE id = $taskId RETURNING #${this.retrieveColumns}
            """.as(this.parser.singleOpt)
          }
        }
      }
    }
  }

  /**
    * Sets the task for a given user. The user cannot set the status of a task unless the object has
    * been locked by the same user before hand.
    * Will throw an InvalidException if the task status cannot be set due to the current task status
    * Will throw an IllegalAccessException if the user is a guest user, or if the task is locked by
    * a different user.
    *
    * @param task   The task to set the status for
    * @param status The status to set
    * @param user   The user setting the status
    * @return The number of rows updated, should only ever be 1
    */
  def setTaskStatus(task: Task, status: Int, user: User)(implicit c: Option[Connection] = None): Int = {
    if (!Task.isValidStatusProgression(task.status.getOrElse(Task.STATUS_CREATED), status)) {
      throw new InvalidException("Invalid task status supplied.")
    } else if (user.guest) {
      throw new IllegalAccessException("Guest users cannot make edits to tasks.")
    }

    val updatedRows = this.withMRTransaction { implicit c =>
      val updatedRows =
        SQL"""UPDATE tasks t SET status = $status WHERE t.id = (
                                SELECT t2.id FROM tasks t2
                                LEFT JOIN locked l on l.item_id = t2.id AND l.item_type = ${task.itemType.typeId}
                                WHERE t2.id = ${task.id} AND (l.user_id = ${user.id} OR l.user_id IS NULL)
                              )""".executeUpdate()
      // if returning 0, then this is because the item is locked by a different user
      if (updatedRows == 0) {
        throw new IllegalAccessException(s"Current task [${task.id} is locked by another user, cannot update status at this time.")
      }
      this.statusActions.setStatusAction(user, task, status)

      // if you set the status successfully on a task you will lose the lock of that task
      try {
        this.unlockItem(user, task)
      } catch {
        case e: Exception => Logger.warn(e.getMessage)
      }
      if (config.changeSetEnabled) {
        // try and match the current task with a changeset from the user
        Future {
          c.commit()
          this.matchToOSMChangeSet(task.copy(status = Some(status)), user)
        }
      }
      updatedRows
    }

    this.cacheManager.withOptionCaching { () => Some(task.copy(status = Some(status))) }
    updatedRows
  }

  /**
    * Tries to match a specific changeset in OSM to the task in MapRoulette
    *
    * @param task The task that was fixed
    * @param user The user making the request
    * @param c An implicit connection
    */
  def matchToOSMChangeSet(task:Task, user:User, immediate:Boolean=true)(implicit c:Option[Connection]=None) : Future[Boolean] = {
    val result = Promise[Boolean]
    if (config.allowMatchOSM) {
      task.status match {
        case Some(Task.STATUS_FIXED) =>
          val currentDateTimeUTC = DateTime.now(DateTimeZone.UTC)
          val statusAction = statusActions.getStatusActions(task, user, Some(List(Task.STATUS_FIXED))).headOption
          statusAction match {
            case Some(sa) =>
              this.getSortedChangeList(sa) onComplete {
                case Success(response) =>
                  val responseList = response.map(_.id)
                  response.find(c => {
                    // check the bounding box of the changeset, and make sure that the task geometry
                    // bounding box at the very least intersects with the changeset bounding box
                    if (c.hasMapRouletteComment) {
                      true
                    } else {
                      val feature = GeoJSONFactory.create(task.geometries).asInstanceOf[FeatureCollection]
                      val reader = new GeoJSONReader()
                      val envelope = new Envelope()
                      feature.getFeatures.foreach(f => {
                        val current = reader.read(f.getGeometry)
                        envelope.expandToInclude(current.getBoundary.getEnvelopeInternal)
                      })

                      val changesetEnvelope = new Envelope(c.minLon, c.maxLon, c.minLat, c.maxLat)
                      changesetEnvelope.intersects(envelope)
                    }
                  }) match {
                    case Some(change) =>
                      this.withMRConnection { implicit c =>
                        Logger.debug(s"Updating task [${task.id}] with changeset [${change.id}]")
                        SQL(s"""UPDATE tasks SET changeset_id = ${change.id} WHERE id = ${task.id}""").executeUpdate()
                      }
                      result success true
                    case None =>
                      this.withMRConnection { implicit c =>
                        Logger.debug(s"No changeset found for user ${sa.osmUserId} on Task [${task.id}] from changesets [${responseList.mkString(",")}]")
                        // if we can't find any viable option set the id to -2 so that we don't try again
                        // but only set it to -2 if the current time is 1 hour after the set time for the task
                        if (Math.abs(currentDateTimeUTC.getMillis - sa.created.getMillis) > (config.changeSetTimeLimit.toHours * 3600 * 1000)) {
                          SQL(s"""UPDATE tasks SET changeset_id = -2 WHERE id = ${task.id}""").executeUpdate()
                        }
                      }
                      result success false
                  }
                case Failure(error) => result success false
              }
            case None => result success false
          }
        case _ =>
          // throw some message here about something
          result success false
      }
    } else {
      result success false
    }
    result.future
  }

  /**
    * This gets the sorted list of changesets. The sorting is based on how close a changeset is to the time
    * that the task was fixed. It probably would be more efficient to
    *
    * @param statusAction The StatusActionItem that is for the action that set the task to fixed
    * @return A list of sorted changesets
    */
  private def getSortedChangeList(statusAction:StatusActionItem) : Future[Seq[Changeset]] = {
    val result = Promise[Seq[Changeset]]
    val format = "YYYY-MM-dd'T'HH:mm:ss'Z'"
    val fixedTimeDiff = statusAction.created.getMillis
    val prevHours = statusAction.created.minusHours(config.changeSetTimeLimit.toHours.toInt).toString(format)
    val nextHours = statusAction.created.plusHours(config.changeSetTimeLimit.toHours.toInt).toString(format)
    ws.url(s"${config.getOSMServer}/api/0.6/changesets?user=${statusAction.osmUserId}&time=$prevHours,$nextHours")
      .withHeaders("User-Agent" -> "MapRoulette").get() onComplete {
      case Success(response) =>
        if (response.status == 200) {
          val changeSetList = ChangesetParser.parse(XML.loadString(response.body)).filter(!_.open)
          val sortedList = changeSetList.sortWith((c1, c2) => {
            Math.abs(c1.createdAt.getMillis - fixedTimeDiff) < Math.abs(c2.createdAt.getMillis - fixedTimeDiff)
          })
          result success sortedList
        } else {
          result failure new InvalidException(s"Response failed with status ${response.status} messages ${response.statusText}")
        }
      case Failure(error) => result failure error
    }
    result.future
  }

  /**
    * Simple query to retrieve the next task in the sequence
    *
    * @param parentId      The parent of the task
    * @param currentTaskId The current task that we are basing our query from
    * @param statusList    the list of status' to filter by
    * @return An optional task, if no more tasks in the list will retrieve the first task
    */
  def getNextTaskInSequence(parentId: Long, currentTaskId: Long, statusList: Option[Seq[Int]] = None)
                           (implicit c: Option[Connection] = None): Option[(Task, Lock)] = {
    this.withMRConnection { implicit c =>
      val lp = for {
        task <- parser
        lock <- lockedParser
      } yield task -> lock
      val query =
        s"""SELECT locked.*, tasks.$retrieveColumns FROM tasks
                      LEFT JOIN locked ON locked.item_id = tasks.id
                      WHERE tasks.id > $currentTaskId AND tasks.parent_id = $parentId
                      AND status IN ({statusList})
                      ORDER BY tasks.id ASC LIMIT 1"""
      val slist = statusList.getOrElse(Task.statusMap.keys.toSeq) match {
        case Nil => Task.statusMap.keys.toSeq
        case t => t
      }
      SQL(query).on('statusList -> slist).as(lp.*).headOption match {
        case Some(t) => Some(t)
        case None =>
          val loopQuery =
            s"""SELECT locked.*, tasks.$retrieveColumns FROM tasks
                              LEFT JOIN locked ON locked.item_id = tasks.id
                              WHERE tasks.parent_id = $parentId
                              AND status IN ({statusList})
                              ORDER BY tasks.id ASC LIMIT 1"""
          SQL(loopQuery).on('statusList -> slist).as(lp.*).headOption
      }
    }
  }

  /**
    * Simple query to retrieve the previous task in the sequence
    *
    * @param parentId      The parent of the task
    * @param currentTaskId The current task that we are basing our query from
    * @param statusList    the list of status' to filter by
    * @return An optional task, if no more tasks in the list will retrieve the last task
    */
  def getPreviousTaskInSequence(parentId: Long, currentTaskId: Long, statusList: Option[Seq[Int]] = None)
                               (implicit c: Option[Connection] = None): Option[(Task, Lock)] = {
    this.withMRConnection { implicit c =>
      val lp = for {
        task <- parser
        lock <- lockedParser
      } yield task -> lock
      val query =
        s"""SELECT locked.*, tasks.$retrieveColumns FROM tasks
                      LEFT JOIN locked ON locked.item_id = tasks.id
                      WHERE tasks.id < $currentTaskId AND tasks.parent_id = $parentId
                      AND status IN ({statusList})
                      ORDER BY tasks.id DESC LIMIT 1"""
      val slist = statusList.getOrElse(Task.statusMap.keys.toSeq) match {
        case Nil => Task.statusMap.keys.toSeq
        case t => t
      }
      SQL(query).on('statusList -> slist).as(lp.*).headOption match {
        case Some(t) => Some(t)
        case None =>
          val loopQuery =
            s"""SELECT locked.*, tasks.$retrieveColumns FROM tasks
                              LEFT JOIN locked ON locked.item_id = tasks.id
                              WHERE tasks.parent_id = $parentId
                              AND status IN ({statusList})
                              ORDER BY tasks.id DESC LIMIT 1"""
          SQL(loopQuery).on('statusList -> slist).as(lp.*).headOption
      }
    }
  }

  def getRandomTasksWithPriority(user: User, params: SearchParameters, limit: Int = -1, proximityId:Option[Long] = None)
                                (implicit c: Option[Connection] = None): List[Task] = {
    val highPriorityTasks = Try(this.getRandomTasks(user, params, limit, Some(Challenge.PRIORITY_HIGH), proximityId)) match {
      case Success(res) => res
      case Failure(f) => List.empty
    }
    if (highPriorityTasks.isEmpty) {
      val mediumPriorityTasks = Try(this.getRandomTasks(user, params, limit, Some(Challenge.PRIORITY_MEDIUM), proximityId)) match {
        case Success(res) => res
        case Failure(f) => List.empty
      }
      if (mediumPriorityTasks.isEmpty) {
        this.getRandomTasks(user, params, limit, Some(Challenge.PRIORITY_LOW), proximityId)
      } else {
        mediumPriorityTasks
      }
    } else {
      highPriorityTasks
    }
  }

  /**
    * Retrieves a random challenge from the list of possible challenges in the search list
    *
    * @param params The params to search for the random challenge
    * @param c The connection to the database, will create one if not already created
    * @return The id of the random challenge
    */
  private def getRandomChallenge(params: SearchParameters)(implicit c:Option[Connection]=None) : Option[Long] = {
    params.getChallengeIds match {
      case Some(v) if v.lengthCompare(1) == 0 => Some(v.head)
      case v =>
        withMRConnection { implicit c =>
          val parameters = new ListBuffer[NamedParameter]()
          val whereClause = new StringBuilder
          val joinClause = new StringBuilder

          v match {
            case Some(l) if l.nonEmpty => appendInWhereClause(whereClause, s"c.id IN (${l.mkString(",")})")
            case None => // ignore
          }

          if (params.enabledChallenge) {
            appendInWhereClause(whereClause, "c.enabled = true")
          }
          if (params.enabledProject) {
            appendInWhereClause(whereClause, "p.enabled = true")
          }

          parameters ++= addChallengeTagMatchingToQuery(params, whereClause, joinClause)
          parameters ++= addSearchToQuery(params, whereClause)

          //add a where clause that just makes sure that any random challenge retrieved actually has some tasks in it
          appendInWhereClause(whereClause, "1 = (SELECT 1 FROM tasks WHERE parent_id = c.id LIMIT 1)")

          val query = s"""
                        SELECT c.id FROM challenges c
                        INNER JOIN projects p ON p.id = c.parent_id
                        ${joinClause.toString}
                        WHERE ${whereClause.toString}
                        ORDER BY RANDOM() LIMIT 1
                      """
          sqlWithParameters(query, parameters).as(long("id").*).headOption
        }
    }
  }

  /**
    * Gets a random task. This will first retrieve a random challenge from the list of criteria for the
    * challenges. If a challenge id is provided all other search criteria will be ignored. Generally
    * this would be called to get a single random task, if multiple random tasks are requested all
    * the random tasks will be retrieved from the random challenge that was selected. So for multiple
    * tasks it will be all grouped from the same challenge.
    *
    * @param user The user executing the request
    * @param params The search parameters that will define the filters for the random selection
    * @param limit The amount of tags that should be returned
    * @param priority An optional priority, so that we only look for tasks in a specific priority range
    * @param proximityId Id of task that you wish to find the next task based on the proximity of that task
    * @return A list of random tags matching the above criteria, an empty list if none match
    */
  def getRandomTasks(user:User, params: SearchParameters, limit:Int = -1,
                     priority:Option[Int]=None, proximityId:Option[Long] = None)
                    (implicit c:Option[Connection]=None) : List[Task] = {
    getRandomChallenge(params) match {
      case Some(challengeId) =>
        val taskTagIds = if (params.hasTaskTags) {
          this.tagDAL.retrieveListByName(params.taskTags.get.map(_.toLowerCase)).map(_.id)
        } else {
          List.empty
        }

        val select =
          s"""SELECT tasks.$retrieveColumns FROM tasks
          LEFT JOIN locked l ON l.item_id = tasks.id
       """.stripMargin

        val parameters = new ListBuffer[NamedParameter]()
        val queryBuilder = new StringBuilder
        // The default where clause will check to see if the parents are enabled, that the task is
        // not locked (or if it is, it is locked by the current user) and that the status of the task
        // is either Created or Skipped
        val taskStatusList = params.taskStatus match {
          case Some(l) if l.nonEmpty => l
          case _ => List(Task.STATUS_CREATED, Task.STATUS_SKIPPED, Task.STATUS_TOO_HARD)
        }
        val whereClause = new StringBuilder(
          s"""WHERE tasks.parent_id = $challengeId AND
              (l.id IS NULL OR l.user_id = ${user.id}) AND
              tasks.status IN ({statusList})
            """)
        parameters += ('statusList -> ParameterValue.toParameterValue(taskStatusList))

        priority match {
          case Some(p) => appendInWhereClause(whereClause, s"tasks.priority = $p")
          case None => //Ignore
        }

        if (taskTagIds.nonEmpty) {
          queryBuilder ++= "INNER JOIN tags_on_tasks tt ON tt.task_id = tasks.id "
          appendInWhereClause(whereClause, "tt.tag_id IN ({tagids})")
          parameters += ('tagids -> ParameterValue.toParameterValue(taskTagIds))
        }
        if (params.taskSearch.nonEmpty) {
          appendInWhereClause(whereClause, s"${searchField("tasks.name", "taskSearch")(None)}")
          parameters += ('taskSearch -> search(params.taskSearch.getOrElse("")))
        }

        val proximityOrdering = proximityId match {
          case Some(id) =>
            // This where clause will make sure that the user doesn't see the same task multiple times in the same hour.
            // It addresses a specific issue with proximity that can cause a user to get into an infinite loop
            appendInWhereClause(whereClause,
              s"""NOT tasks.id IN (
                 |SELECT task_id FROM status_actions
                 |WHERE osm_user_id IN (${user.osmProfile.id})
                 |  AND created >= NOW() - '1 hour'::INTERVAL
                 |UNION SELECT $id)""".stripMargin)
            s"ST_Distance(tasks.location, (SELECT location FROM tasks WHERE id = $id)),"
          case None => ""
        }

        val query = s"$select ${queryBuilder.toString} ${whereClause.toString} " +
          s"ORDER BY $proximityOrdering tasks.status, RANDOM() LIMIT ${this.sqlLimit(limit)}"

        implicit val ids = List[Long]()
        this.cacheManager.withIDListCaching { implicit cachedItems =>
          this.withListLocking(user, Some(TaskType())) { () =>
            this.withMRTransaction { implicit c =>
              sqlWithParameters(query, parameters).as(this.parser.*)
            }
          }
        }
      case None => List.empty
    }
  }

  /**
    * This function will retrieve all the tasks in a given bounded area. You can use various search
    * parameters to limit the tasks retrieved in the bounding box area.
    *
    * @param params The search parameters from the cookie or the query string parameters.
    * @param limit A limit for the number of returned tasks
    * @param offset This allows paging for the tasks within in the bounding box
    * @param c An available connection
    * @return The list of Tasks found within the bounding box
    */
  def getTasksInBoundingBox(params:SearchParameters, limit:Int = Config.DEFAULT_LIST_SIZE, offset:Int = 0)
                           (implicit c:Option[Connection] = None) : List[ClusteredPoint] = {
    params.location match {
      case Some(sl) =>
        withMRConnection { implicit c =>
          val parameters = new ListBuffer[NamedParameter]()
          val whereClause = new StringBuilder(
            s"""
               WHERE t.location @ ST_MakeEnvelope (${sl.left}, ${sl.bottom}, ${sl.right}, ${sl.top}, 4326)
               AND p.deleted = false AND c.deleted = false
              """
          )
          val joinClause = new StringBuilder(
            """
              INNER JOIN challenges c ON c.id = t.parent_id
              INNER JOIN projects p ON p.id = c.parent_id
            """
          )

          params.taskStatus match {
            case Some(sl) if sl.nonEmpty => appendInWhereClause(whereClause, s"t.status IN ($sl)")
            case _ => appendInWhereClause(whereClause, "t.status IN (0,3,6)")
          }

          params.priority match {
            case Some(p) if p == 0 || p == 1 || p == 2 => appendInWhereClause(whereClause, s"t.priority = $p")
            case _ => // ignore
          }

          parameters ++= addSearchToQuery(params, whereClause)
          parameters ++= addChallengeTagMatchingToQuery(params, whereClause, joinClause)

          val query =
            s"""
              SELECT t.id, t.name, t.parent_id, c.name, t.instruction, t.status,
                     ST_AsGeoJSON(t.location) AS location, priority FROM tasks t
              ${joinClause.toString()}
              ${whereClause.toString()}
              ORDER BY RANDOM()
              LIMIT ${sqlLimit(limit)} OFFSET $offset
            """
          val pointParser = long("tasks.id") ~ str("tasks.name") ~ int("tasks.parent_id") ~ str("challenges.name") ~
                            str("tasks.instruction") ~ str("location") ~ int("tasks.status") ~ int("tasks.priority") map {
            case id ~ name ~ parentId ~ parentName ~ instruction ~ location ~ status ~ priority =>
              val locationJSON = Json.parse(location)
              val coordinates = (locationJSON \ "coordinates").as[List[Double]]
              val point = Point(coordinates(1), coordinates.head)
              ClusteredPoint(id, -1, "", name, parentId, parentName, point, JsString(""),
                instruction, DateTime.now(), -1, Actions.ITEM_TYPE_TASK, status, priority)
          }
          sqlWithParameters(query, parameters).as(pointParser.*)
        }
      case None => throw new InvalidException("Bounding Box required to retrieve tasks within a bounding box")
    }
  }

  /**
    * Returns the list of users that modified the requested task
    *
    * @param user  The user making the request
    * @param id    The id of the task
    * @param limit The number of distinct users that modified the task
    * @param c     An implicit connection to use, if not found will create a new connection
    * @return A list of users that modified the task
    */
  def getLastModifiedUser(user: User, id: Long, limit: Int = 1)(implicit c: Option[Connection] = None): List[User] = {
    withMRConnection { implicit c =>
      val query =
        s"""
           |SELECT ${userDAL.get().retrieveColumns} FROM users WHERE osm_id IN (
           |  SELECT osm_user_id FROM status_actions WHERE task_id = {id}
           |  ORDER BY created DESC
           |  LIMIT {limit}
           |)
         """.stripMargin
      SQL(query).on('id -> id, 'limit -> limit).as(userDAL.get().parser.*)
    }
  }

  /**
    * Retrieves a specific comment
    *
    * @param commentId The id for the comment
    * @param c
    * @return An optional comment
    */
  def retrieveComment(commentId:Long)(implicit c:Option[Connection] = None) : Option[Comment] = {
    withMRConnection { implicit c =>
      SQL("""SELECT * FROM task_comments tc
              INNER JOIN users u ON u.osm_id = tc.osm_id
              WHERE tc.id = {commentId}"""
      ).on('commentId -> commentId).as(this.commentParser.*).headOption
    }
  }

  /**
    * Retrieves all the comments for a task, challenge or project
    *
    * @param projectIdList A list of all project ids to match on
    * @param challengeIdList A list of all challenge ids to match on
    * @param taskIdList A list of all task ids to match on
    * @param limit limit the number of tasks in the response
    * @param offset for paging
    * @param c
    * @return The list of comments for the task
    */
  def retrieveComments(projectIdList:List[Long], challengeIdList:List[Long], taskIdList:List[Long],
                       limit:Int = Config.DEFAULT_LIST_SIZE, offset:Int = 0)(implicit c:Option[Connection] = None) : List[Comment] = {
    withMRConnection { implicit c =>
      val whereClause = new StringBuilder("")
      if (projectIdList.nonEmpty) {
        this.appendInWhereClause(whereClause, s"project_id IN (${projectIdList.mkString(",")})")
      }
      if (challengeIdList.nonEmpty) {
        this.appendInWhereClause(whereClause, s"challenge_id IN (${challengeIdList.mkString(",")})")
      }
      if (taskIdList.nonEmpty) {
        this.appendInWhereClause(whereClause, s"challenge_id IN (${taskIdList.mkString(",")})")
      }

      SQL(s"""
              SELECT * FROM task_comments tc
              INNER JOIN users u ON u.osm_id = tc.osm_id
              WHERE $whereClause
              ORDER BY tc.project_id, tc.challenge_id, tc.created DESC
              LIMIT ${this.sqlLimit(limit)} OFFSET $offset
          """
      ).as(this.commentParser.*)
    }
  }

  /**
    * Add comment to a task
    *
    * @param user The user adding the comment
    * @param taskId The task that you are adding the comment too
    * @param comment The actual comment
    * @param actionId the id for the action if any action associated
    * @param c
    */
  def addComment(user:User, taskId:Long, comment:String, actionId:Option[Long])(implicit c:Option[Connection] = None) : Comment = {
    withMRConnection { implicit c =>
      if (StringUtils.isEmpty(comment)) {
        throw new InvalidException("Invalid empty string supplied.")
      }
      val query =
        s"""
           |INSERT INTO task_comments (osm_id, task_id, comment, action_id)
           |VALUES ({osm_id}, {task_id}, {comment}, {action_id}) RETURNING id, project_id, challenge_id
         """.stripMargin
      SQL(query).on('osm_id -> user.osmProfile.id,
                    'task_id -> taskId,
                    'comment -> comment,
                    'action_id -> actionId).as((long("id") ~ long("project_id") ~ long("challenge_id")).*).headOption match {
        case Some(ids) =>
          Comment(ids._1._1, user.osmProfile.id, user.name, taskId, ids._1._2, ids._2, DateTime.now(), comment, actionId)
        case None => throw new Exception("Failed to add comment")
      }
    }
  }

  /**
    * Updates a comment that a user previously set
    *
    * @param user The user updating the comment, it has to be the original user who made the comment
    * @param commentId The id for the original comment
    * @param updatedComment The new comment
    * @param c
    * @return The updated comment
    */
  def updateComment(user:User, commentId:Long, updatedComment:String)(implicit c:Option[Connection] = None) : Comment = {
    withMRConnection { implicit c =>
      if (StringUtils.isEmpty(updatedComment)) {
        throw new InvalidException("Invalid empty string supplied.")
      }
      // first get the comment
      this.retrieveComment(commentId) match {
        case Some(original) =>
          if (!user.isSuperUser && original.osm_id != user.osmProfile.id) {
            throw new IllegalAccessException("User updating the comment must be a Super user or the original user who made the comment")
          }
          SQL("UPDATE task_comments SET comment = {comment} WHERE id = {id}")
            .on('comment -> updatedComment, 'id -> commentId).executeUpdate()
          original.copy(comment = updatedComment)
        case None => throw new NotFoundException("Original comment does not exist")
      }
    }
  }

  /**
    * Deletes a comment from a task
    *
    * @param user The user deleting the comment, only super user or challenge admin can delete
    * @param taskId The task that the comment is associated with
    * @param commentId The id for the comment being deleted
    * @param c
    */
  def deleteComment(user:User, taskId:Long, commentId:Long)(implicit c:Option[Connection] = None) : Unit = {
    withMRConnection { implicit c =>
      this.retrieveById(taskId) match {
        case Some(task) =>
          this.permission.hasObjectWriteAccess(task, user)
          SQL("DELETE FROM task_comments WHERE id = {id}").on('id -> commentId)
        case None =>
          throw new NotFoundException("Task was not found.")
      }
    }
  }
}
