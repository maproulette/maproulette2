// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.models.dal

import java.sql.Connection

import anorm.SqlParser._
import anorm._
import javax.inject.{Inject, Provider, Singleton}
import org.apache.commons.lang3.StringUtils
import org.joda.time.DateTime
import org.maproulette.Config
import org.maproulette.cache.CacheManager
import org.maproulette.data.{Actions, ChallengeType, ProjectType, TaskType}
import org.maproulette.exception.{InvalidException, NotFoundException, UniqueViolationException}
import org.maproulette.models._
import org.maproulette.permissions.Permission
import org.maproulette.session.{SearchParameters, User}
import play.api.db.Database
import play.api.libs.json.{JsString, JsValue, Json}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

/**
  * The challenge data access layer handles all calls for challenges going to the database. Most
  * worked is delegated to the ParentDAL and BaseDAL, but a couple of specific function like
  * insert and update found here.
  *
  * @author cuthbertm
  */
@Singleton
class ChallengeDAL @Inject()(override val db: Database, taskDAL: TaskDAL,
                             override val tagDAL: TagDAL,
                             projectDAL: Provider[ProjectDAL],
                             override val permission: Permission)
  extends ParentDAL[Long, Challenge, Task] with TagDALMixin[Challenge] with OwnerMixin[Challenge] {

  import scala.concurrent.ExecutionContext.Implicits.global
  private val DEFAULT_NUM_CHILDREN_LIST = 1000

  // The manager for the challenge cache
  override val cacheManager = new CacheManager[Long, Challenge]
  // The name of the challenge table
  override val tableName: String = "challenges"
  // The name of the table for it's children Tasks
  override val childTable: String = "tasks"
  // The row parser for it's children defined in the TaskDAL
  override val childParser = taskDAL.parser
  override val childColumns: String = taskDAL.retrieveColumns
  override val retrieveColumns: String = "*, ST_AsGeoJSON(location) AS locationJSON, ST_AsGeoJSON(bounding) AS boundingJSON"

  /**
    * The row parser for Anorm to enable the object to be read from the retrieved row directly
    * to the Challenge object.
    */
  override val parser: RowParser[Challenge] = {
    get[Long]("challenges.id") ~
      get[String]("challenges.name") ~
      get[DateTime]("challenges.created") ~
      get[DateTime]("challenges.modified") ~
      get[Option[String]]("challenges.description") ~
      get[Option[String]]("challenges.info_link") ~
      get[Long]("challenges.owner_id") ~
      get[Long]("challenges.parent_id") ~
      get[String]("challenges.instruction") ~
      get[Int]("challenges.difficulty") ~
      get[Option[String]]("challenges.blurb") ~
      get[Boolean]("challenges.enabled") ~
      get[Int]("challenges.challenge_type") ~
      get[Boolean]("challenges.featured") ~
      get[Option[Int]]("challenges.popularity") ~
      get[Option[String]]("challenges.checkin_comment") ~
      get[Option[String]]("challenges.checkin_source") ~
      get[Option[String]]("challenges.overpass_ql") ~
      get[Option[String]]("challenges.remote_geo_json") ~
      get[Option[Int]]("challenges.status") ~
      get[Option[String]]("challenges.status_message") ~
      get[Int]("challenges.default_priority") ~
      get[Option[String]]("challenges.high_priority_rule") ~
      get[Option[String]]("challenges.medium_priority_rule") ~
      get[Option[String]]("challenges.low_priority_rule") ~
      get[Int]("challenges.default_zoom") ~
      get[Int]("challenges.min_zoom") ~
      get[Int]("challenges.max_zoom") ~
      get[Option[Int]]("challenges.default_basemap") ~
      get[Option[String]]("challenges.default_basemap_id") ~
      get[Option[String]]("challenges.custom_basemap") ~
      get[Boolean]("challenges.updatetasks") ~
      get[Option[DateTime]]("challenges.last_task_refresh") ~
      get[Option[String]]("locationJSON") ~
      get[Option[String]]("boundingJSON") ~
      get[Boolean]("deleted") map {
      case id ~ name ~ created ~ modified ~ description ~ infoLink ~ ownerId ~ parentId ~ instruction ~
        difficulty ~ blurb ~ enabled ~ challenge_type ~ featured ~ popularity ~ checkin_comment ~ checkin_source ~ overpassql ~ remoteGeoJson ~
        status ~ statusMessage ~ defaultPriority ~ highPriorityRule ~ mediumPriorityRule ~ lowPriorityRule ~
        defaultZoom ~ minZoom ~ maxZoom ~ defaultBasemap ~ defaultBasemapId ~ customBasemap ~ updateTasks ~ lastTaskRefresh ~ location ~
        bounding ~ deleted =>
        val hpr = highPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        val mpr = mediumPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        val lpr = lowPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        new Challenge(id, name, created, modified, description, deleted, infoLink,
          ChallengeGeneral(ownerId, parentId, instruction, difficulty, blurb, enabled, challenge_type, featured, popularity, checkin_comment.getOrElse(""), checkin_source.getOrElse(""), None),
          ChallengeCreation(overpassql, remoteGeoJson),
          ChallengePriority(defaultPriority, hpr, mpr, lpr),
          ChallengeExtra(defaultZoom, minZoom, maxZoom, defaultBasemap, defaultBasemapId, customBasemap, updateTasks),
          status, statusMessage, lastTaskRefresh, location, bounding,
        )
    }
  }

  /**
    * The row parser for Anorm to enable the object to be read from the retrieved row directly
    * to the Challenge object.
    */
  val withVirtualParentParser: RowParser[Challenge] = {
    get[Long]("challenges.id") ~
      get[String]("challenges.name") ~
      get[DateTime]("challenges.created") ~
      get[DateTime]("challenges.modified") ~
      get[Option[String]]("challenges.description") ~
      get[Option[String]]("challenges.info_link") ~
      get[Long]("challenges.owner_id") ~
      get[Long]("challenges.parent_id") ~
      get[String]("challenges.instruction") ~
      get[Int]("challenges.difficulty") ~
      get[Option[String]]("challenges.blurb") ~
      get[Boolean]("challenges.enabled") ~
      get[Int]("challenges.challenge_type") ~
      get[Boolean]("challenges.featured") ~
      get[Option[Int]]("challenges.popularity") ~
      get[Option[String]]("challenges.checkin_comment") ~
      get[Option[String]]("challenges.checkin_source") ~
      get[Option[String]]("challenges.overpass_ql") ~
      get[Option[String]]("challenges.remote_geo_json") ~
      get[Option[Int]]("challenges.status") ~
      get[Option[String]]("challenges.status_message") ~
      get[Int]("challenges.default_priority") ~
      get[Option[String]]("challenges.high_priority_rule") ~
      get[Option[String]]("challenges.medium_priority_rule") ~
      get[Option[String]]("challenges.low_priority_rule") ~
      get[Int]("challenges.default_zoom") ~
      get[Int]("challenges.min_zoom") ~
      get[Int]("challenges.max_zoom") ~
      get[Option[Int]]("challenges.default_basemap") ~
      get[Option[String]]("challenges.default_basemap_id") ~
      get[Option[String]]("challenges.custom_basemap") ~
      get[Boolean]("challenges.updatetasks") ~
      get[Option[DateTime]]("challenges.last_task_refresh") ~
      get[Option[String]]("locationJSON") ~
      get[Option[String]]("boundingJSON") ~
      get[Boolean]("deleted") ~
      get[Option[Array[Long]]]("virtual_parent_ids") map {
      case id ~ name ~ created ~ modified ~ description ~ infoLink ~ ownerId ~ parentId ~ instruction ~
        difficulty ~ blurb ~ enabled ~ challenge_type ~ featured ~ popularity ~ checkin_comment ~ checkin_source ~ overpassql ~ remoteGeoJson ~
        status ~ statusMessage ~ defaultPriority ~ highPriorityRule ~ mediumPriorityRule ~ lowPriorityRule ~
        defaultZoom ~ minZoom ~ maxZoom ~ defaultBasemap ~ defaultBasemapId ~ customBasemap ~ updateTasks ~ lastTaskRefresh ~ location ~
        bounding ~ deleted ~ virtualParents =>
        val hpr = highPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        val mpr = mediumPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        val lpr = lowPriorityRule match {
          case Some(c) if StringUtils.isEmpty(c) || StringUtils.equals(c, "{}") => None
          case r => r
        }
        new Challenge(id, name, created, modified, description, deleted, infoLink,
          ChallengeGeneral(ownerId, parentId, instruction, difficulty, blurb, enabled, challenge_type, featured, popularity, checkin_comment.getOrElse(""), checkin_source.getOrElse(""), virtualParents),
          ChallengeCreation(overpassql, remoteGeoJson),
          ChallengePriority(defaultPriority, hpr, mpr, lpr),
          ChallengeExtra(defaultZoom, minZoom, maxZoom, defaultBasemap, defaultBasemapId, customBasemap, updateTasks),
          status, statusMessage, lastTaskRefresh, location, bounding,
        )
    }
  }

  val pointParser: RowParser[ClusteredPoint] = {
    get[Long]("tasks.id") ~
      get[String]("tasks.name") ~
      get[Long]("tasks.parent_id") ~
      get[String]("challenges.name") ~
      get[String]("tasks.instruction") ~
      get[String]("location") ~
      get[Int]("tasks.status") ~
      get[Option[DateTime]]("tasks.mapped_on") ~
      get[Int]("tasks.priority") ~
      get[Option[Int]]("task_review.review_status") ~
      get[Option[Int]]("task_review.review_requested_by") ~
      get[Option[Int]]("task_review.reviewed_by") ~
      get[Option[DateTime]]("task_review.reviewed_at") map {
      case id ~ name ~ parentId ~ parentName ~ instruction ~ location ~ status ~
           mappedOn ~ priority ~ reviewStatus ~ reviewRequestedBy ~ reviewedBy ~
           reviewedAt =>
        val locationJSON = Json.parse(location)
        val coordinates = (locationJSON \ "coordinates").as[List[Double]]
        val point = Point(coordinates(1), coordinates.head)
        ClusteredPoint(id, -1, "", name, parentId, parentName, point, JsString(""),
          instruction, DateTime.now(), -1, Actions.ITEM_TYPE_TASK, status, mappedOn,
          reviewStatus, reviewRequestedBy, reviewedBy, reviewedAt, priority)
    }
  }

  val listingParser: RowParser[ChallengeListing] = {
    get[Long]("challenges.id") ~
      get[Long]("challenges.parent_id") ~
      get[String]("challenges.name") ~
      get[Boolean]("challenges.enabled") ~
      get[Option[Array[Long]]]("virtual_parent_ids") map {
      case id ~ parent ~ name ~ enabled ~ virtualParents =>
        ChallengeListing(id, parent, name, enabled, virtualParents)
    }
  }

  /**
    * A basic retrieval of the object based on the id. With caching, so if it finds
    * the object in the cache it will return that object without checking the database, otherwise
    * will hit the database directly.
    *
    * @param id The id of the object to be retrieved
    * @return The object, None if not found
    */
  override def retrieveById(implicit id: Long, c: Option[Connection] = None): Option[Challenge] = {
    this.cacheManager.withCaching { () =>
      this.withMRConnection { implicit c =>
        val query =
          s"""
            |SELECT c.$retrieveColumns, array_remove(array_agg(vp.project_id), NULL) AS virtual_parent_ids
            |FROM challenges c
            |LEFT OUTER JOIN virtual_project_challenges vp ON c.id = vp.challenge_id
            |WHERE c.id = {id}
            |GROUP BY c.id
           """.stripMargin

        SQL(query).on('id -> id).as(this.withVirtualParentParser.singleOpt)
      }
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
  override def retrieveRootObject(obj: Either[Long, Challenge], user: User)(implicit c: Option[Connection] = None): Option[Project] = {
    obj match {
      case Left(id) =>
        this.permission.hasReadAccess(ChallengeType(), user)(id)
        this.projectDAL.get().cacheManager.withOptionCaching { () =>
          this.withMRConnection { implicit c =>
            SQL"""SELECT p.* FROM projects p
             INNER JOIN challenges c ON c.parent_id = p.id
             WHERE c.id = $id
           """.as(projectDAL.get().parser.*).headOption
          }
        }
      case Right(challenge) =>
        this.permission.hasObjectReadAccess(challenge, user)
        this.projectDAL.get().cacheManager.withOptionCaching { () =>
          this.withMRConnection { implicit c =>
            SQL"""SELECT * FROM projects WHERE id = ${challenge.general.parent}""".as(projectDAL.get().parser.*).headOption
          }
        }
    }
  }

  /**
    * Inserts a new Challenge object into the database. It will also place it in the cache after
    * inserting the object.
    *
    * @param challenge The challenge to insert into the database
    * @return The object that was inserted into the database. This will include the newly created id
    */
  override def insert(challenge: Challenge, user: User)(implicit c: Option[Connection] = None): Challenge = {
    this.projectDAL.get().retrieveById(challenge.general.parent) match {
      case Some(project) =>
        if (project.isVirtual.getOrElse(false)) {
          throw new InvalidException(s"Challenge cannot be created in a virtual project.")
        }
      case _ => throw new InvalidException(s"Cannot create challenge. Project is invalid.")
    }

    this.permission.hasObjectWriteAccess(challenge, user)
    this.cacheManager.withOptionCaching { () =>
      this.withMRTransaction { implicit c =>
        SQL"""INSERT INTO challenges (name, owner_id, parent_id, difficulty, description, info_link, blurb,
                                      instruction, enabled, challenge_type, featured, checkin_comment, checkin_source,
                                      overpass_ql, remote_geo_json, status, status_message, default_priority, high_priority_rule,
                                      medium_priority_rule, low_priority_rule, default_zoom, min_zoom,
                                      max_zoom, default_basemap, default_basemap_id, custom_basemap, updatetasks)
              VALUES (${challenge.name}, ${challenge.general.owner}, ${challenge.general.parent}, ${challenge.general.difficulty},
                      ${challenge.description}, ${challenge.infoLink}, ${challenge.general.blurb}, ${challenge.general.instruction},
                      ${challenge.general.enabled}, ${challenge.general.challengeType}, ${challenge.general.featured},
                      ${challenge.general.checkinComment}, ${challenge.general.checkinSource}, ${challenge.creation.overpassQL}, ${challenge.creation.remoteGeoJson}, ${challenge.status},
                      ${challenge.statusMessage}, ${challenge.priority.defaultPriority}, ${challenge.priority.highPriorityRule},
                      ${challenge.priority.mediumPriorityRule}, ${challenge.priority.lowPriorityRule}, ${challenge.extra.defaultZoom}, ${challenge.extra.minZoom},
                      ${challenge.extra.maxZoom}, ${challenge.extra.defaultBasemap}, ${challenge.extra.defaultBasemapId}, ${challenge.extra.customBasemap}, ${challenge.extra.updateTasks}
                      ) ON CONFLICT(parent_id, LOWER(name)) DO NOTHING RETURNING #${this.retrieveColumns}""".as(this.parser.*).headOption
      }
    } match {
      case Some(value) => value
      case None => throw new UniqueViolationException(s"Challenge with name ${challenge.name} already exists in the database")
    }
  }

  /**
    * Updates a challenge. Uses the updatingCache so will first retrieve the object and make sure
    * to update only values supplied by the json. After updated will update the cache as well
    *
    * @param updates The updates in json format
    * @param id      The id of the object that you are updating
    * @return An optional object, it will return None if no object found with a matching id that was supplied
    */
  override def update(updates: JsValue, user: User)(implicit id: Long, c: Option[Connection] = None): Option[Challenge] = {
    var updatedPriorityRules = false
    val updatedChallenge = this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit cachedItem =>
      this.permission.hasObjectWriteAccess(cachedItem, user)
      val highPriorityRule = (updates \ "highPriorityRule").asOpt[String].getOrElse(cachedItem.priority.highPriorityRule.getOrElse("")) match {
        case x if Challenge.isValidRule(Some(x)) => x
        case _ => ""
      }
      val mediumPriorityRule = (updates \ "mediumPriorityRule").asOpt[String].getOrElse(cachedItem.priority.mediumPriorityRule.getOrElse("")) match {
        case x if Challenge.isValidRule(Some(x)) => x
        case _ => ""
      }
      val lowPriorityRule = (updates \ "lowPriorityRule").asOpt[String].getOrElse(cachedItem.priority.lowPriorityRule.getOrElse("")) match {
        case x if Challenge.isValidRule(Some(x)) => x
        case _ => ""
      }
      this.withMRTransaction { implicit c =>
        val name = (updates \ "name").asOpt[String].getOrElse(cachedItem.name)
        val ownerId = (updates \ "ownerId").asOpt[Long].getOrElse(cachedItem.general.owner)
        val parentId = (updates \ "parentId").asOpt[Long].getOrElse(cachedItem.general.parent)
        val difficulty = (updates \ "difficulty").asOpt[Int].getOrElse(cachedItem.general.difficulty)
        val description = (updates \ "description").asOpt[String].getOrElse(cachedItem.description.getOrElse(""))
        val infoLink = (updates \ "infoLink").asOpt[String].getOrElse(cachedItem.infoLink.getOrElse(""))
        val challengeType = (updates \ "challengeType").asOpt[Int].getOrElse(cachedItem.general.challengeType)
        val blurb = (updates \ "blurb").asOpt[String].getOrElse(cachedItem.general.blurb.getOrElse(""))
        val instruction = (updates \ "instruction").asOpt[String].getOrElse(cachedItem.general.instruction)
        val enabled = (updates \ "enabled").asOpt[Boolean].getOrElse(cachedItem.general.enabled)
        val featured = (updates \ "featured").asOpt[Boolean].getOrElse(cachedItem.general.featured)
        val checkinComment = (updates \ "checkinComment").asOpt[String].getOrElse(cachedItem.general.checkinComment)
        val checkinSource = (updates \ "checkinSource").asOpt[String].getOrElse(cachedItem.general.checkinSource)
        val overpassQL = (updates \ "overpassQL").asOpt[String].getOrElse(cachedItem.creation.overpassQL.getOrElse(""))
        val remoteGeoJson = (updates \ "remoteGeoJson").asOpt[String].getOrElse(cachedItem.creation.remoteGeoJson.getOrElse(""))
        val status = (updates \ "status").asOpt[Int].getOrElse(cachedItem.status.getOrElse(Challenge.STATUS_NA))
        val statusMessage = (updates \ "statusMessage").asOpt[String].getOrElse(cachedItem.statusMessage.getOrElse(""))
        val defaultPriority = (updates \ "defaultPriority").asOpt[Int].getOrElse(cachedItem.priority.defaultPriority)
        // if any of the priority rules have changed then we need to run the update priorities task
        if (!StringUtils.equalsIgnoreCase(highPriorityRule, cachedItem.priority.highPriorityRule.getOrElse("")) ||
          !StringUtils.equalsIgnoreCase(mediumPriorityRule, cachedItem.priority.mediumPriorityRule.getOrElse("")) ||
          !StringUtils.equalsIgnoreCase(lowPriorityRule, cachedItem.priority.lowPriorityRule.getOrElse("")) ||
          defaultPriority != cachedItem.priority.defaultPriority) {
          updatedPriorityRules = true
        }
        val defaultZoom = (updates \ "defaultZoom").asOpt[Int].getOrElse(cachedItem.extra.defaultZoom)
        val minZoom = (updates \ "minZoom").asOpt[Int].getOrElse(cachedItem.extra.minZoom)
        val maxZoom = (updates \ "maxZoom").asOpt[Int].getOrElse(cachedItem.extra.maxZoom)
        val defaultBasemap = (updates \ "defaultBasemap").asOpt[Int].getOrElse(cachedItem.extra.defaultBasemap.getOrElse(-1))
        val defaultBasemapId = (updates \ "defaultBasemapId").asOpt[String].getOrElse(cachedItem.extra.defaultBasemapId.getOrElse(""))
        val customBasemap = (updates \ "customBasemap").asOpt[String].getOrElse(cachedItem.extra.customBasemap.getOrElse(""))
        val updateTasks = (updates \ "updateTasks").asOpt[Boolean].getOrElse(cachedItem.extra.updateTasks)

        SQL"""UPDATE challenges SET name = $name, owner_id = $ownerId, parent_id = $parentId, difficulty = $difficulty,
                description = $description, info_link = $infoLink, blurb = $blurb, instruction = $instruction,
                enabled = $enabled, featured = $featured, checkin_comment = $checkinComment, checkin_source = $checkinSource, overpass_ql = $overpassQL,
                remote_geo_json = $remoteGeoJson, status = $status, status_message = $statusMessage, default_priority = $defaultPriority,
                high_priority_rule = ${
          if (StringUtils.isEmpty(highPriorityRule)) {
            Option.empty[String]
          } else {
            Some(highPriorityRule)
          }
        },
                medium_priority_rule = ${
          if (StringUtils.isEmpty(mediumPriorityRule)) {
            Option.empty[String]
          } else {
            Some(mediumPriorityRule)
          }
        },
                low_priority_rule = ${
          if (StringUtils.isEmpty(lowPriorityRule)) {
            Option.empty[String]
          } else {
            Some(lowPriorityRule)
          }
        },
                default_zoom = $defaultZoom, min_zoom = $minZoom, max_zoom = $maxZoom, default_basemap = $defaultBasemap, default_basemap_id = $defaultBasemapId,
                custom_basemap = $customBasemap, updatetasks = $updateTasks, challenge_type = $challengeType
              WHERE id = $id RETURNING #${this.retrieveColumns}""".as(parser.*).headOption
      }
    }
    // update the task priorities in the background
    if (updatedPriorityRules) {
      Future {
        updateTaskPriorities(user)
      }
    }
    updatedChallenge
  }

  /**
    * Will run through the tasks in batches of 50 and update the priorities based on the rules
    * of the challenge
    *
    * @param user The user executing the request
    * @param id   The id of the challenge
    * @param c    The connection for the request
    */
  def updateTaskPriorities(user: User)(implicit id: Long, c: Option[Connection] = None): Unit = {
    this.permission.hasWriteAccess(TaskType(), user)
    this.withMRConnection { implicit c =>
      val challenge = this.retrieveById(id) match {
        case Some(c) => c
        case None => throw new NotFoundException(s"Could not update priorties for tasks, no challenge with id $id found.")
      }
      // make sure that at least one of the challenges is valid
      if (Challenge.isValidRule(challenge.priority.highPriorityRule) ||
        Challenge.isValidRule(challenge.priority.mediumPriorityRule) ||
        Challenge.isValidRule(challenge.priority.lowPriorityRule)) {
        var pointer = 0
        var currentTasks: List[Task] = List.empty
        do {
          currentTasks = listChildren(DEFAULT_NUM_CHILDREN_LIST, pointer)
          val highPriorityIDs = currentTasks.filter(_.getTaskPriority(challenge) == Challenge.PRIORITY_HIGH).map(_.id).mkString(",")
          val mediumPriorityIDs = currentTasks.filter(_.getTaskPriority(challenge) == Challenge.PRIORITY_MEDIUM).map(_.id).mkString(",")
          val lowPriorityIDs = currentTasks.filter(_.getTaskPriority(challenge) == Challenge.PRIORITY_LOW).map(_.id).mkString(",")

          if (highPriorityIDs.nonEmpty) {
            SQL"""UPDATE tasks SET priority = ${Challenge.PRIORITY_HIGH} WHERE id IN (#$highPriorityIDs)""".executeUpdate()
          }
          if (mediumPriorityIDs.nonEmpty) {
            SQL"""UPDATE tasks SET priority = ${Challenge.PRIORITY_MEDIUM} WHERE id IN (#$mediumPriorityIDs)""".executeUpdate()
          }
          if (lowPriorityIDs.nonEmpty) {
            SQL"""UPDATE tasks SET priority = ${Challenge.PRIORITY_LOW} WHERE id IN (#$lowPriorityIDs)""".executeUpdate()
          }

          pointer += 1
        } while (currentTasks.size == 50)
        this.taskDAL.clearCaches
      }
    }
  }

  /**
    * Lists the children of the parent, override the base functionality and includes the geojson
    * as part of the query so that it doesn't have to fetch it each and every time.
    *
    * @param limit  limits the number of children to be returned
    * @param offset For paging, ie. the page number starting at 0
    * @param id     The parent ID
    * @return A list of children objects
    */
  override def listChildren(limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0, onlyEnabled: Boolean = false, searchString: String = "",
                            orderColumn: String = "id", orderDirection: String = "ASC")(implicit id: Long, c: Option[Connection] = None): List[Task] = {
    // add a child caching option that will keep a list of children for the parent
    this.withMRConnection { implicit c =>
      // slightly different from the standard parser, in that it retrieves the geojson within the sql
      val geometryParser: RowParser[Task] = {
        get[Long]("tasks.id") ~
          get[String]("tasks.name") ~
          get[DateTime]("tasks.created") ~
          get[DateTime]("tasks.modified") ~
          get[Long]("parent_id") ~
          get[Option[String]]("tasks.instruction") ~
          get[Option[String]]("location") ~
          get[String]("geometry") ~
          get[Option[String]]("suggestedFix") ~
          get[Option[Int]]("tasks.status") ~
          get[Option[DateTime]]("tasks.mapped_on") ~
          get[Option[Int]]("task_review.review_status") ~
          get[Option[Long]]("task_review.review_requested_by") ~
          get[Option[Long]]("task_review.reviewed_by") ~
          get[Option[DateTime]]("task_review.reviewed_at") ~
          get[Option[Long]]("task_review.review_claimed_by") ~
          get[Int]("tasks.priority") map {
          case id ~ name ~ created ~ modified ~ parent_id ~ instruction ~ location ~
               geometry ~ suggestedFix ~ status ~ mappedOn ~ reviewStatus ~ reviewRequestedBy ~
               reviewedBy ~ reviewedAt ~ reviewClaimedBy ~ priority =>
            Task(id, name, created, modified, parent_id, instruction, location,
                 geometry, suggestedFix, status, mappedOn, reviewStatus, reviewRequestedBy,
                 reviewedBy, reviewedAt, reviewClaimedBy, priority)
        }
      }

      val query =
        s"""SELECT ${taskDAL.retrieveColumns},
                        (SELECT row_to_json(fc)::text as geometries
                          FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features
                                  FROM ( SELECT 'Feature' As type,
                                                ST_AsGeoJSON(lg.geom)::json As geometry,
                                                hstore_to_json(lg.properties) As properties
                                        FROM task_geometries As lg
                                        WHERE task_id = tasks.id
                                      ) As f
                                ) As fc)::text AS geometry,
                        (SELECT row_to_json(fc)::text as geometries
                           FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features
                                  FROM ( SELECT 'Feature' As type,
                                                  ST_AsGeoJSON(lg.geom)::json As geometry,
                                                  hstore_to_json(lg.properties) As properties
                                         FROM task_geometries As lg
                                         WHERE task_id = tasks.id
                                       ) As f
                                 ) As fc)::text AS suggestedFix
                      FROM tasks
                      LEFT OUTER JOIN task_review ON task_review.task_id = tasks.id
                      WHERE parent_id = {id} ${this.enabled(onlyEnabled)}
                      ${this.searchField("name")}
                      ${this.order(orderColumn = Some("tasks." + orderColumn), orderDirection = orderDirection, nameFix = true)}
                      LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> this.search(searchString),
        'id -> ToParameterValue.apply[Long](p = keyToStatement).apply(id),
        'offset -> offset)
        .as(geometryParser.*)
    }
  }

  override def find(searchString: String, limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0, onlyEnabled: Boolean = false,
                    orderColumn: String = "id", orderDirection: String = "ASC")
                   (implicit parentId: Long = -1, c: Option[Connection] = None): List[Challenge] =
    this.findByType(searchString, limit, offset, onlyEnabled, orderColumn, orderDirection)

  def findByType(searchString: String, limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0, onlyEnabled: Boolean = false,
                 orderColumn: String = "id", orderDirection: String = "ASC", challengeType: Int = Actions.ITEM_TYPE_CHALLENGE)
                (implicit parentId: Long = -1, c: Option[Connection] = None): List[Challenge] = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns} FROM challenges c
                      INNER JOIN projects p ON p.id = c.parent_id
                      WHERE challenge_type = $challengeType AND c.deleted = false AND p.deleted = false
                      ${this.searchField("c.name")}
                      ${this.enabled(onlyEnabled, "p")} ${this.enabled(onlyEnabled, "c")}
                      ${this.parentFilter(parentId)}
                      ${this.order(Some(orderColumn), orderDirection, "c", true)}
                      LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('ss -> searchString, 'offset -> offset).as(this.parser.*)
    }
  }

  def listing(projectList: Option[List[Long]] = None, limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0,
              onlyEnabled: Boolean = false, challengeType: Int = Actions.ITEM_TYPE_CHALLENGE): List[ChallengeListing] = {
    this.withMRConnection { implicit c =>
      var projectFilter = ""
      if (projectList != None) {
        implicit val conjunction = None
        projectFilter =
          s"""AND (${this.getLongListFilter(projectList, "p.id")} OR c.id IN
                 (SELECT challenge_id FROM virtual_project_challenges vp WHERE
                  ${getLongListFilter(projectList, "vp.project_id")}))"""
      }

      val query =
        s"""SELECT c.id, c.parent_id, c.name, c.enabled, array_remove(array_agg(vp.project_id), NULL) AS virtual_parent_ids FROM challenges c
                      INNER JOIN projects p ON p.id = c.parent_id
                      LEFT OUTER JOIN virtual_project_challenges vp ON c.id = vp.challenge_id
                      WHERE challenge_type = $challengeType AND c.deleted = false AND p.deleted = false
                      ${this.enabled(onlyEnabled, "p")} ${this.enabled(onlyEnabled, "c")}
                      ${projectFilter}
                      GROUP BY c.id
                      LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
      SQL(query).on('offset -> offset).as(this.listingParser.*)
    }
  }

  override def list(limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0, onlyEnabled: Boolean = false, searchString: String = "",
                    orderColumn: String = "id", orderDirection: String = "ASC")
                   (implicit parentId: Long = -1, c: Option[Connection] = None): List[Challenge] =
    this.listByType(limit, offset, onlyEnabled, searchString, orderColumn, orderDirection)

  /**
    * This is a dangerous function as it will return all the objects available, so it could take up
    * a lot of memory
    */
  def listByType(limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0, onlyEnabled: Boolean = false, searchString: String = "",
                 orderColumn: String = "id", orderDirection: String = "ASC", challengeType: Int = Actions.ITEM_TYPE_CHALLENGE)
                (implicit parentId: Long = -1, c: Option[Connection] = None): List[Challenge] = {
    implicit val ids = List.empty
    this.cacheManager.withIDListCaching { implicit uncachedIDs =>
      this.withMRConnection { implicit c =>
        val query =
          s"""SELECT $retrieveColumns FROM challenges c
                        INNER JOIN projects p ON p.id = c.parent_id
                        WHERE challenge_type = $challengeType
                        ${this.searchField("c.name")}
                        ${this.enabled(onlyEnabled, "p")} ${this.enabled(onlyEnabled, "c")}
                        ${this.parentFilter(parentId)}
                        ${this.order(Some(orderColumn), orderDirection, "c", true)}
                        LIMIT ${this.sqlLimit(limit)} OFFSET {offset}"""
        SQL(query).on('ss -> this.search(searchString),
          'offset -> ToParameterValue.apply[Int].apply(offset)
        ).as(this.parser.*)
      }
    }
  }

  /**
    * Gets the featured challenges
    *
    * @param limit       The number of challenges to retrieve
    * @param offset      For paging, ie. the page number starting at 0
    * @param enabledOnly if true will only return enabled challenges
    * @return list of challenges
    */
  def getFeaturedChallenges(limit: Int, offset: Int, enabledOnly: Boolean = true)(implicit c: Option[Connection] = None): List[Challenge] = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns} FROM challenges c
                      INNER JOIN projects p ON p.id = c.parent_id
                      WHERE featured = TRUE ${this.enabled(enabledOnly, "c")} ${this.enabled(enabledOnly, "p")}
                      AND c.deleted = false and p.deleted = false
                      AND (c.status <> ${Challenge.STATUS_BUILDING} AND
                           c.status <> ${Challenge.STATUS_FAILED} AND
                           c.status <> ${Challenge.STATUS_FINISHED})
                      AND 0 < (SELECT COUNT(*) FROM tasks WHERE parent_id = c.id)
                      LIMIT ${this.sqlLimit(limit)} OFFSET $offset"""
      SQL(query).as(this.parser.*)
    }
  }

  /**
    * Get the Hot challenges, these are challenges that have proved popular, weighted
    * towards recent activity.
    *
    * @param limit       the number of challenges to retrieve
    * @param offset      For paging, ie. the page number starting at 0
    * @param enabledOnly if true will only return enabled challenges
    * @return List of challenges
    */
  def getHotChallenges(limit: Int, offset: Int, enabledOnly: Boolean = true)(implicit c: Option[Connection] = None): List[Challenge] = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns} FROM challenges c
                      INNER JOIN projects p ON p.id = c.parent_id
                      WHERE c.deleted = false and p.deleted = false
                      ${this.enabled(enabledOnly, "c")} ${this.enabled(enabledOnly, "p")}
                      AND (c.status <> ${Challenge.STATUS_BUILDING} AND
                           c.status <> ${Challenge.STATUS_FAILED} AND
                           c.status <> ${Challenge.STATUS_FINISHED})
                      AND 0 < (SELECT COUNT(*) FROM tasks WHERE parent_id = c.id)
                      ORDER BY popularity DESC LIMIT ${this.sqlLimit(limit)} OFFSET $offset"""
      SQL(query).as(this.parser.*)
    }
  }

  /**
    * Gets the new challenges
    *
    * @param limit       The number of challenges to retrieve
    * @param offset      For paging ie. the page number starting at 0
    * @param enabledOnly if true will only return enabled challenges
    * @return list of challenges
    */
  def getNewChallenges(limit: Int, offset: Int, enabledOnly: Boolean = true)(implicit c: Option[Connection] = None): List[Challenge] = {
    this.withMRConnection { implicit c =>
      val query =
        s"""SELECT ${this.retrieveColumns} FROM challenges c
                      INNER JOIN projects p ON c.parent_id = p.id
                      WHERE ${this.enabled(enabledOnly, "c")(None)} ${this.enabled(enabledOnly, "p")}
                      AND c.deleted = false and p.deleted = false
                      AND (c.status <> ${Challenge.STATUS_BUILDING} AND
                           c.status <> ${Challenge.STATUS_FAILED} AND
                           c.status <> ${Challenge.STATUS_FINISHED})
                      ${this.order(Some("created"), "DESC", "c", true)}
                      LIMIT ${this.sqlLimit(limit)} OFFSET $offset"""
      SQL(query).as(this.parser.*)
    }
  }

  /**
    * Gets the combined geometry of all the tasks that are associated with the challenge
    *
    * @param challengeId  The id for the challenge
    * @param statusFilter To view the geojson for only challenges with a specific status
    * @param c            The implicit connection for the function
    * @return
    */
  def getChallengeGeometry(challengeId: Long, statusFilter: Option[List[Int]] = None)(implicit c: Option[Connection] = None): String = {
    this.withMRConnection { implicit c =>
      val filter = statusFilter match {
        case Some(s) => s"AND status IN (${s.mkString(",")}"
        case None => ""
      }
      SQL"""SELECT row_to_json(fc)::text as geometries
            FROM ( SELECT 'FeatureCollection' As type, array_to_json(array_agg(f)) As features
                   FROM ( SELECT 'Feature' As type,
                                  ST_AsGeoJSON(lg.geom)::json As geometry,
                                  hstore_to_json(lg.properties) As properties
                          FROM task_geometries As lg
                          WHERE task_id IN
                          (SELECT DISTINCT id FROM tasks WHERE parent_id = $challengeId) #$filter
                    ) As f
            )  As fc""".as(str("geometries").single)
    }
  }

  /**
    * This retrieves all the tasks geojson as line by line. When using this format it is a lot easier to
    * rebuild a challenge correctly.
    *
    * @param challengeId The id of the challenge
    * @param c           The implicit connection
    * @return A map of Task ID to geojson string
    */
  def getLineByLineChallengeGeometry(challengeId: Long)(implicit c: Option[Connection] = None): Map[Long, String] = {
    this.withMRConnection { implicit c =>
      SQL"""SELECT t.id, (
                  SELECT row_to_json(fc)::text as geometries
                  FROM ( SELECT task_id, 'FeatureCollection' As type, array_to_json(array_agg(f)) As features
                          FROM ( SELECT task_id, 'Feature' As type,
             					                ST_AsGeoJSON(lg.geom)::json As geometry,
             					                hstore_to_json(lg.properties) As properties
             					           FROM task_geometries As lg
             					           WHERE task_id = t.id
             				          ) As f
             			            GROUP BY task_id
             	        )  As fc
                  ) AS geometry
              FROM tasks t
              WHERE t.parent_id = $challengeId
        """.as((long("tasks.id") ~ str("geometry")).*).map(x => x._1 -> x._2).toMap
    }
  }

  /**
    * Retrieves the json that contains the central points for all the tasks
    *
    * @param challengeId  The id of the challenge
    * @param statusFilter Filter the displayed task cluster points by their status
    * @return A list of clustered point objects
    */
  def getClusteredPoints(challengeId: Long, statusFilter: Option[List[Int]] = None, limit: Int = 2500)
                        (implicit c: Option[Connection] = None): List[ClusteredPoint] = {
    this.withMRConnection { implicit c =>
      val filter = statusFilter match {
        case Some(s) => s"AND status IN (${s.mkString(",")}"
        case None => ""
      }
      SQL"""SELECT t.id, t.name, t.instruction, t.status, t.mapped_on,
                   t.parent_id, tr.review_status, tr.review_requested_by,
                   tr.reviewed_by, tr.reviewed_at, c.name,
                   ST_AsGeoJSON(t.location) AS location, t.priority
            FROM tasks t
            INNER JOIN challenges c ON c.id = t.parent_id
            INNER JOIN projects p ON p.id = c.parent_id
            LEFT OUTER JOIN task_review tr ON tr.task_id = t.id
            WHERE t.parent_id = $challengeId
              AND p.deleted = false AND c.deleted = false
              AND ST_AsGeoJSON(t.location) IS NOT NULL
            #$filter
            LIMIT #${sqlLimit(limit)}"""
        .as(this.pointParser.*)
    }
  }

  /**
    * The summary for a challenge is the status with the number of tasks associated with each status
    * underneath the given challenge
    *
    * @param id The id for the challenge
    * @return Map of status codes mapped to task counts
    */
  def getSummary(id: Long)(implicit c: Option[Connection] = None): Map[Int, Int] = {
    this.withMRConnection { implicit c =>
      val summaryParser = int("count") ~ get[Option[Int]]("tasks.status") map {
        case count ~ status => status.getOrElse(0) -> count
      }
      SQL"""SELECT COUNT(*) as count, status FROM tasks WHERE parent_id = $id GROUP BY status"""
        .as(summaryParser.*).toMap
    }
  }

  /**
    * Removes challenge tasks in CREATED or SKIPPED statuses, intended to be used
    * in preparation for rebuilding with fresh task data
    *
    * @param user The user executing the request
    * @param id   The id of the challenge
    * @param c    The connection for the request
    */
  def removeIncompleteTasks(user: User)(implicit id: Long, c: Option[Connection] = None): Unit = {
    this.permission.hasWriteAccess(ChallengeType(), user)
    this.withMRConnection { implicit c =>
      SQL"""DELETE from tasks WHERE parent_id = ${id} and status IN (${Task.STATUS_CREATED}, ${Task.STATUS_SKIPPED})""".execute()

      this.taskDAL.clearCaches
    }
  }

  /**
    * Moves a challenge from one project to another. You are required to have admin access on both
    * the current project and the project you are moving the challenge too
    *
    * @param newParent   The id of the new parent project
    * @param challengeId The id of the challenge that you are moving
    * @param c           an implicit connection
    */
  def moveChallenge(newParent: Long, challengeId: Long, user: User)(implicit c: Option[Connection] = None): Option[Challenge] = {
    this.permission.hasAdminAccess(ProjectType(), user)(newParent)
    implicit val id = challengeId
    this.retrieveById match {
      case Some(c) => this.permission.hasObjectAdminAccess(c, user)
      case None => throw new NotFoundException(s"No challenge with id $challengeId found.")
    }
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit item =>
      this.withMRTransaction { implicit c =>
        val movedChallenge = SQL"UPDATE challenges SET parent_id = $newParent WHERE id = $challengeId RETURNING #${this.retrieveColumns}".as(this.parser.*).headOption

        // Also update status_actions so we don't lose our history
        SQL"UPDATE status_actions SET project_id = $newParent WHERE challenge_id = $challengeId".execute()

        movedChallenge
      }
    }
  }

  /**
    * Update the popularity score of the given challenge following completion of a task.
    * Challenge popularity p is calculated with the simple formula p = (p + t) / 2 where
    * t is the timestamp of the task completion. This favors recent activity.
    *
    * @param id                  The id of the challenge
    * @param completionTimestamp the unix timestamp of the task completion
    */
  def updatePopularity(completionTimestamp: Long)(implicit id: Long, c: Option[Connection] = None): Option[Challenge] = {
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit item =>
      this.withMRTransaction { implicit c =>
        SQL"UPDATE challenges SET popularity=((popularity + $completionTimestamp) / 2) WHERE id = $id RETURNING #${this.retrieveColumns}".as(this.parser.*).headOption
      }
    }
  }

  /**
    * Updates the challenge to a STATUS_FINISHED if there are no incomplete tasks left.
    *
    * @param id The id of the challenge
    */
  def updateFinishedStatus()(implicit id: Long, c: Option[Connection] = None): Unit = {
    this.withMRConnection { implicit c =>
      this.retrieveById(id) match {
        case Some(challenge) =>
          if (challenge.status.getOrElse(Challenge.STATUS_NA) == Challenge.STATUS_READY) {
            this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit item =>
              // If the challenge has no tasks in the created status it need to be marked finished.
              val updateStatusQuery =
                s"""UPDATE challenges SET status = ${Challenge.STATUS_FINISHED}
                            WHERE id = ${id} AND status = ${Challenge.STATUS_READY} AND
                            0 = (SELECT COUNT(*) AS total FROM tasks
                                      WHERE tasks.parent_id = ${id}
                                      AND status = ${Task.STATUS_CREATED})
                            RETURNING ${this.retrieveColumns}"""

              SQL(updateStatusQuery).as(this.parser.*).headOption
            }
            Option(challenge)
          }
        case None =>
          throw new NotFoundException(s"No challenge found with id $id")
      }
    }
  }

  /**
    * Updates the challenge to a STATUS_READY if there are incomplete tasks left.
    *
    * @param id                  The id of the challenge
    * @param completionTimestamp the unix timestamp of the task completion
    */
  def updateReadyStatus()(implicit id: Long, c: Option[Connection] = None): Option[Challenge] = {
    this.withMRConnection { implicit c =>
      this.retrieveById(id) match {
        case Some(challenge) =>
          if (challenge.status.getOrElse(Challenge.STATUS_NA) == Challenge.STATUS_FINISHED) {
            this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit item =>
              // If the challenge was finished and any tasks were reset back to created
              // we need to set the challenge status back to ready
              val updateStatusQuery2 =
                s"""UPDATE challenges SET status = ${Challenge.STATUS_READY}
                            WHERE id = $id AND status = ${Challenge.STATUS_FINISHED} AND
                            0 != (SELECT COUNT(*) AS total FROM tasks
                                      WHERE tasks.parent_id = $id
                                      AND status = ${Task.STATUS_CREATED})
                            RETURNING ${this.retrieveColumns}"""

              SQL(updateStatusQuery2).as(this.parser.*).headOption
            }
          }
          Option(challenge)
        case None =>
          throw new NotFoundException(s"No challenge found with id $id")
      }
    }
  }

  /**
    * Updates the last_task_refresh column of a challenge to indicate its task
    * data was just refreshed. If overwrite is false, last_task_refresh will
    * only be updated if it hasn't yet been set.
    *
    * @param overwrite Set to true to always overwrite last_task_refresh
    * @param id        The id of the challenge
    * @param c         an implicit connection
    */
  def markTasksRefreshed(overwrite: Boolean = false)(implicit id: Long, c: Option[Connection] = None): Option[Challenge] = {
    this.cacheManager.withUpdatingCache(Long => retrieveById) { implicit item =>
      if (overwrite || item.lastTaskRefresh == None) {
        this.withMRTransaction { implicit c =>
          SQL"UPDATE challenges SET last_task_refresh = NOW() WHERE id = $id RETURNING #${this.retrieveColumns}".as(this.parser.*).headOption
        }
      }
      else {
        Option(item)
      }
    }
  }

  /**
    * Resets all the Task instructions for the children of the challenge
    *
    * @param challengeId The id of the parent challenge
    * @param user        A super user or the owner/admin of the challenge
    * @param c
    */
  def resetTaskInstructions(user: User, challengeId: Long)(implicit c: Option[Connection] = None): Unit = {
    this.withMRConnection { implicit c =>
      this.retrieveById(challengeId) match {
        case Some(challenge) =>
          if (challenge.general.instruction.isEmpty) {
            throw new InvalidException("Cannot reset Task instructions if there is no Challenge instruction available.")
          }
          this.permission.hasObjectAdminAccess(challenge, user)
          SQL("UPDATE tasks SET instruction = '' WHERE parent_id = {id}").on('id -> challengeId).executeUpdate()
        case None =>
          throw new NotFoundException(s"No challenge found with id $challengeId")
      }
    }
  }

  /**
    * Deletes all the tasks in a challenge
    *
    * @param user         The user making the deletion request
    * @param challengeId  The id for the parent challenge
    * @param statusFilter Filter the deletion by Task status, if empty will ignore status and just delete all Task children
    * @param c
    */
  def deleteTasks(user: User, challengeId: Long, statusFilter: List[Int] = List.empty)(implicit c: Option[Connection] = None): Unit = {
    this.withMRConnection { implicit c =>
      val filter = if (statusFilter.isEmpty) {
        ""
      } else {
        s"AND status IN (${statusFilter.mkString(",")}"
      }
      val query = s"""DELETE FROM tasks WHERE parent_id = {challengeId} $filter"""
      SQL(query).on('challengeId -> challengeId).executeUpdate()
    }
  }

  /**
    * The extended find function will return a list of challenges based on the search critieria provided in the
    * query string. It will apply ONLY the following parameters:
    * - projectId (pid) = If searching only within a particular project, and the MapRoulette ID for that project
    * - projectSearch (ps) = A text search based on the name of the project, will be ignored if projectId has been set
    * - projectEnabled (pe) = Whether to only include projects that are enabled, will be ignored if projectId has been set, by default will be true
    * - challengeEnabled (ce) = Whether to only include challenges that are enabled, by default will be true
    * - challengeSearch (cs) = A text search based on the name of the challenge
    * - challengeTags (ct) = A comma separated list of tags to check if the challenge contains any of the
    * provided tags, can use challengeTagConjunction (ctt) to switch from inclusive to exclusive
    * - challengeTagConjunction (ctc) = Whether the challenge tag list is inclusive or exclusive. True
    * means exclusive, false inclusive. By default is inclusive
    * - location (tbb) = Provide a bounding box to limit the search window by
    * - bounding (bb) = Provide a bounding box that will search for any challenge bounding box intersections
    * - owner (o) = Optionally can search by all challenges created by a specific owner
    *
    * @param searchParameters The object that contains all the search parameters that were retrieved from the query string
    * @param limit            limit for the number of returned results
    * @param offset           The paging offset
    * @param sort             An optional column to sort by.
    * @param order            Direction of ordering ASC or DESC.
    * @param c                An optional connection, if included will use that connection otherwise will grab one from the pool
    * @return A list of challenges, empty list if not challenges found matching the given criteria
    */
  def extendedFind(searchParameters: SearchParameters, limit: Int = Config.DEFAULT_LIST_SIZE, offset: Int = 0,
                   sort: String = "", order: String = "")
                  (implicit c: Option[Connection] = None): List[Challenge] = {
    this.withMRConnection { implicit c =>
      val parameters = new ListBuffer[NamedParameter]()
      // never include deleted items in the search
      val whereClause = new StringBuilder("c.deleted = false AND p.deleted = false")
      val joinClause = new StringBuilder()
      var orderByClause = ""

      parameters ++= addSearchToQuery(searchParameters, whereClause)

      parameters ++= addChallengeTagMatchingToQuery(searchParameters, whereClause, joinClause)

      searchParameters.owner match {
        case Some(o) if o.nonEmpty =>
          joinClause ++= "INNER JOIN users u ON u.id = c.owner_id"
          this.appendInWhereClause(whereClause, s"u.name = {owner}")
          parameters += ('owner -> o)
        case _ => // ignore
      }

      searchParameters.location match {
        case Some(l) =>
          this.appendInWhereClause(whereClause, s"(c.location && ST_MakeEnvelope(${l.left}, ${l.bottom}, ${l.right}, ${l.top}, 4326))")
        case None =>
      }

      searchParameters.bounding match {
        case Some(b) =>
          this.appendInWhereClause(whereClause, s"ST_Intersects(c.bounding, ST_MakeEnvelope(${b.left}, ${b.bottom}, ${b.right}, ${b.top}, 4326))")
        case None =>
      }

      searchParameters.projectEnabled match {
        case Some(true) => this.appendInWhereClause(whereClause, this.enabled(true, "p")(None))
        case _ =>
      }

      searchParameters.challengeEnabled match {
        case Some(true) => this.appendInWhereClause(whereClause, this.enabled(true, "c")(None))
        case _ =>
      }

      searchParameters.challengeDifficulty match {
        case Some(v) if v > 0 && v < 4 => this.appendInWhereClause(whereClause, s"c.difficulty = ${v}")
        case _ =>
      }

      searchParameters.challengeStatus match {
        case Some(sl) if sl.nonEmpty =>
          val statusClause = new StringBuilder(s"(c.status IN (${sl.mkString(",")})")
          if (sl.contains(-1)) {
            statusClause ++= " OR c.status IS NULL"
          }
          statusClause ++= ")"
          this.appendInWhereClause(whereClause, statusClause.toString())
        case Some(sl) if sl.isEmpty => //ignore this scenario
        case _ =>
      }

      sort match {
        case s if s.nonEmpty =>
          orderByClause = this.order(Some(s), order, "c", false, s == "name")
        case _ => // ignore
      }

      val query =
        s"""
           |SELECT c.${this.retrieveColumns}, array_remove(array_agg(vp.project_id), NULL) AS virtual_parent_ids FROM challenges c
           |INNER JOIN projects p ON p.id = c.parent_id
           |LEFT OUTER JOIN virtual_project_challenges vp ON c.id = vp.challenge_id
           |$joinClause
           |${s"WHERE $whereClause"}
           |GROUP BY c.id
           |${orderByClause}
           |LIMIT ${this.sqlLimit(limit)} OFFSET $offset
         """.stripMargin

      sqlWithParameters(query, parameters).as(this.withVirtualParentParser.*)
    }
  }
}
